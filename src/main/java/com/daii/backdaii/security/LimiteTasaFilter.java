package com.daii.backdaii.security;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.exception.ErrorResponse;
import com.daii.backdaii.service.AuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rate limit for the whole API (SEC-06), after authentication so a signed-in user is counted as
 * themselves and not as their IP. Some endpoints add their own, tighter limit. Not a bean on
 * purpose: it is added to the security chain only, never registered twice as a servlet filter.
 */
public class LimiteTasaFilter extends OncePerRequestFilter {

    private final LimitadorDeIntentos limitador;
    private final AuditoriaService auditoria;
    private final ObjectMapper objectMapper;

    public LimiteTasaFilter(LimitadorDeIntentos limitador, AuditoriaService auditoria, ObjectMapper objectMapper) {
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !ruta.startsWith("/api/") || ruta.equals("/api/health") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cliente = cliente(request);
        if (superado(LimiteIntentos.API_GENERAL, cliente, request, response)) {
            return;
        }
        if ("POST".equals(request.getMethod()) && request.getRequestURI().equals("/api/reservas")
                && superado(LimiteIntentos.CREAR_RESERVA, cliente, request, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean superado(LimiteIntentos limite, String cliente, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        int intentos = limitador.intentar(limite, cliente);
        if (intentos <= limite.getMaximo()) {
            return false;
        }
        // Once per window, not once per rejected request: a burst would flood the audit log.
        if (intentos == limite.getMaximo() + 1) {
            auditoria.registrar(EventoAuditoria.LIMITE_SUPERADO, ResultadoAuditoria.DENEGADO, usuario(),
                    limite.name() + " en " + request.getMethod() + " " + request.getRequestURI());
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(limite.getVentana().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("Demasiadas solicitudes. Probá de nuevo en un minuto."));
        return true;
    }

    private static String cliente(HttpServletRequest request) {
        String usuario = usuario();
        return usuario != null ? "usuario:" + usuario : "ip:" + request.getRemoteAddr();
    }

    private static String usuario() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.isAuthenticated() && !"anonymousUser".equals(autenticacion.getName())
                ? autenticacion.getName() : null;
    }
}
