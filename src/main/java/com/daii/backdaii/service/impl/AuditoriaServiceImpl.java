package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.RegistroAuditoria;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.domain.repository.AuditoriaRepository;
import com.daii.backdaii.dto.auditoria.PaginaAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServiceImpl.class);
    private static final int TAMANIO_MAXIMO = 100;
    private static final int PAGINA_MAXIMA = 100_000;

    private final AuditoriaRepository repositorio;
    private final Clock reloj;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(EventoAuditoria evento, ResultadoAuditoria resultado, String usuario, String detalle) {
        HttpServletRequest pedido = pedidoActual();
        try {
            repositorio.save(RegistroAuditoria.builder()
                    .fecha(LocalDateTime.now(reloj))
                    .evento(evento)
                    .resultado(resultado)
                    .usuario(recortar(usuario, 150))
                    .ip(pedido == null ? null : recortar(pedido.getRemoteAddr(), 64))
                    .userAgent(pedido == null ? null : recortar(pedido.getHeader("User-Agent"), 300))
                    .detalle(recortar(detalle, 300))
                    .build());
        } catch (RuntimeException e) {
            // Auditing must never break the operation it records.
            log.error("No se pudo registrar el evento de auditoría {}: {}", evento, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaAuditoria buscar(EventoAuditoria evento, String usuario, int pagina, int tamanio) {
        String filtro = usuario == null || usuario.isBlank() ? null : usuario.trim();
        Page<RegistroAuditoria> resultado = repositorio.buscar(evento, filtro,
                PageRequest.of(Math.clamp(pagina, 0, PAGINA_MAXIMA), Math.clamp(tamanio, 1, TAMANIO_MAXIMO)));
        return new PaginaAuditoria(resultado.getContent().stream()
                .map(r -> new PaginaAuditoria.Registro(r.getId(), r.getFecha(), r.getEvento(), r.getResultado(),
                        r.getUsuario(), r.getIp(), r.getUserAgent(), r.getDetalle()))
                .toList(), resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    private static HttpServletRequest pedidoActual() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
                ? atributos.getRequest() : null;
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo);
    }
}
