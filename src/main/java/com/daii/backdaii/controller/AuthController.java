package com.daii.backdaii.controller;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.ConfirmarEmailRequest;
import com.daii.backdaii.dto.auth.EmailDisponibleRequest;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.ReenviarConfirmacionRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.auth.ResultadoAuth;
import com.daii.backdaii.dto.auth.SegundoFactorRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.EmailNoConfirmadoException;
import com.daii.backdaii.exception.NoAutorizadoException;
import com.daii.backdaii.exception.TooManyRequestsException;
import com.daii.backdaii.security.JwtTokenProvider;
import com.daii.backdaii.security.LimitadorDeIntentos;
import com.daii.backdaii.security.LimiteIntentos;
import com.daii.backdaii.security.Sesiones;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * The refresh token lives in an HttpOnly cookie, out of reach of scripts (SEC-02). Strict
     * same-site and scoped to /api/auth: the browser only sends it to refresh and logout.
     */
    static final String COOKIE_REFRESH = "pl_refresh";

    private final AuthService authService;
    private final LimitadorDeIntentos limitador;
    private final AuditoriaService auditoria;
    private final Sesiones sesiones;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<UsuarioDTO> register(@Valid @RequestBody RegistroRequest request) {
        UsuarioDTO usuario = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    // POST keeps the email out of URLs and access logs.
    @PostMapping("/email-disponible")
    public ResponseEntity<Map<String, Boolean>> emailDisponible(@Valid @RequestBody EmailDisponibleRequest request,
                                                                HttpServletRequest httpRequest) {
        if (!limitador.permitir(LimiteIntentos.EMAIL_DISPONIBLE, httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiadas consultas. Probá de nuevo en un minuto.");
        }
        return ResponseEntity.ok(Map.of("disponible", authService.emailDisponible(request.getEmail())));
    }

    @PostMapping("/confirmar-email")
    public ResponseEntity<AuthResponse> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest request) {
        return conSesion(authService.confirmarEmail(request.getToken()));
    }

    @PostMapping("/reenviar-confirmacion")
    public ResponseEntity<Map<String, String>> reenviarConfirmacion(@Valid @RequestBody ReenviarConfirmacionRequest request,
                                                                    HttpServletRequest httpRequest) {
        if (!limitador.permitir(LimiteIntentos.REENVIAR_CONFIRMACION, httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Pediste demasiados links. Probá de nuevo en 15 minutos.");
        }
        authService.reenviarConfirmacion(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Si la cuenta está pendiente de confirmación, te enviamos un link nuevo."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Counted per account, not per IP: the IP can be spoofed and rotated, the target account cannot.
        String cuenta = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (limitador.agotado(LimiteIntentos.LOGIN_FALLIDO, cuenta)) {
            auditoria.registrar(EventoAuditoria.LOGIN, ResultadoAuditoria.DENEGADO, cuenta, "Cuenta trabada por intentos fallidos");
            throw new TooManyRequestsException("Demasiados intentos fallidos. Probá de nuevo en 15 minutos o restablecé tu contraseña.");
        }
        try {
            ResultadoAuth resultado = authService.login(request);
            limitador.reiniciar(LimiteIntentos.LOGIN_FALLIDO, cuenta);
            boolean segundoFactor = Boolean.TRUE.equals(resultado.respuesta().getSegundoFactorRequerido());
            auditoria.registrar(EventoAuditoria.LOGIN, ResultadoAuditoria.EXITO, cuenta,
                    segundoFactor ? "Contraseña correcta, falta el segundo factor" : null);
            return conSesion(resultado);
        } catch (BadCredentialsException e) {
            limitador.permitir(LimiteIntentos.LOGIN_FALLIDO, cuenta);
            auditoria.registrar(EventoAuditoria.LOGIN, ResultadoAuditoria.FALLO, cuenta, "Credenciales incorrectas");
            throw e;
        } catch (EmailNoConfirmadoException e) {
            auditoria.registrar(EventoAuditoria.LOGIN, ResultadoAuditoria.FALLO, cuenta, "Email sin confirmar");
            throw e;
        }
    }

    @PostMapping("/login/segundo-factor")
    public ResponseEntity<AuthResponse> segundoFactor(@Valid @RequestBody SegundoFactorRequest request) {
        // Six digits fall to brute force without a cap: counted per account, so signing in again
        // with the password for a fresh challenge does not reset it.
        String clave = jwtTokenProvider.leerDesafio(request.desafio()).orElse("desafio-invalido");
        if (limitador.agotado(LimiteIntentos.SEGUNDO_FACTOR_FALLIDO, clave)) {
            throw new TooManyRequestsException("Demasiados códigos incorrectos. Ingresá de nuevo en 15 minutos.");
        }
        try {
            return conSesion(authService.segundoFactor(request));
        } catch (NoAutorizadoException e) {
            limitador.permitir(LimiteIntentos.SEGUNDO_FACTOR_FALLIDO, clave);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = COOKIE_REFRESH, required = false) String refresh) {
        try {
            ResultadoAuth resultado = authService.refresh(refresh);
            auditoria.registrar(EventoAuditoria.RENOVACION_SESION, ResultadoAuditoria.EXITO,
                    resultado.respuesta().getUsuario().getEmail(), null);
            return conSesion(resultado);
        } catch (NoAutorizadoException e) {
            if (Sesiones.CODIGO_REUTILIZADO.equals(e.getCode())) {
                auditoria.registrar(EventoAuditoria.REUTILIZACION_REFRESH, ResultadoAuditoria.DENEGADO,
                        sesiones.duenio(refresh), "Refresh ya usado: se revocó la familia de la sesión");
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                    .body(null);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                      @CookieValue(name = COOKIE_REFRESH, required = false) String refresh) {
        authService.logout(authHeader, refresh);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(Map.of("message", "Sesión cerrada correctamente."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal UserDetails user,
                                                       @Valid @RequestBody ChangePasswordRequest request) {
        // The other sessions end; this one continues with the new pair.
        return conSesion(authService.changePassword(user.getUsername(), request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        if (!limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Pediste demasiados links. Probá de nuevo en 15 minutos.");
        }
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Si el email existe, vas a recibir un link para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }

    private ResponseEntity<AuthResponse> conSesion(ResultadoAuth resultado) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (resultado.refresh() != null) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookie(resultado.refresh(), sesiones.duracion()).toString());
        }
        return respuesta.body(resultado.respuesta());
    }

    private static ResponseCookie cookie(String valor, Duration duracion) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(duracion)
                .build();
    }
}
