package com.daii.backdaii.controller;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.ConfirmarEmailRequest;
import com.daii.backdaii.dto.auth.EmailDisponibleRequest;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.ReenviarConfirmacionRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.TooManyRequestsException;
import com.daii.backdaii.security.LimitadorDeIntentos;
import com.daii.backdaii.security.LimiteIntentos;
import com.daii.backdaii.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final LimitadorDeIntentos limitador;

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
        return ResponseEntity.ok(authService.confirmarEmail(request.getToken()));
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
            throw new TooManyRequestsException("Demasiados intentos fallidos. Probá de nuevo en 15 minutos o restablecé tu contraseña.");
        }
        try {
            AuthResponse response = authService.login(request);
            limitador.reiniciar(LimiteIntentos.LOGIN_FALLIDO, cuenta);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            limitador.permitir(LimiteIntentos.LOGIN_FALLIDO, cuenta);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal UserDetails user,
                                                       @Valid @RequestBody ChangePasswordRequest request) {
        // The other sessions end; this one continues with the token in the response.
        return ResponseEntity.ok(authService.changePassword(user.getUsername(), request));
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
}