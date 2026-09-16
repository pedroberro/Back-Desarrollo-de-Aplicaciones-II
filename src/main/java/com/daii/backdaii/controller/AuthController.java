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
import com.daii.backdaii.security.RateLimiter;
import com.daii.backdaii.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Tells whether an account exists, so it is capped per IP to make bulk lookups impractical.
    private final RateLimiter emailDisponibleLimiter = new RateLimiter(20, Duration.ofMinutes(1));
    // Each call can send a mail, so it is capped per IP to avoid flooding someone's inbox.
    private final RateLimiter forgotPasswordLimiter = new RateLimiter(5, Duration.ofMinutes(15));
    private final RateLimiter reenviarConfirmacionLimiter = new RateLimiter(5, Duration.ofMinutes(15));

    @PostMapping("/register")
    public ResponseEntity<UsuarioDTO> register(@Valid @RequestBody RegistroRequest request) {
        UsuarioDTO usuario = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    // POST keeps the email out of URLs and access logs.
    @PostMapping("/email-disponible")
    public ResponseEntity<Map<String, Boolean>> emailDisponible(@Valid @RequestBody EmailDisponibleRequest request,
                                                                HttpServletRequest httpRequest) {
        if (!emailDisponibleLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
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
        if (!reenviarConfirmacionLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Pediste demasiados links. Probá de nuevo en 15 minutos.");
        }
        authService.reenviarConfirmacion(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Si la cuenta está pendiente de confirmación, te enviamos un link nuevo."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal UserDetails user,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getUsername(), request);
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        if (!forgotPasswordLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
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