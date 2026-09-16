package com.daii.backdaii.controller;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.AuthSession;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.service.AuthService;
import com.daii.backdaii.service.AuthSessionService;
import com.daii.backdaii.service.UsuarioService;
import com.daii.backdaii.service.PasswordResetService;
import com.daii.backdaii.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final UsuarioService usuarioService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<UsuarioDTO> register(@Valid @RequestBody RegistroRequest request) {
        UsuarioDTO usuario = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        authService.login(request);
        return withRefreshCookie(authSessionService.create(request.getEmail(), userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken,
                                                @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        if (refreshToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return withRefreshCookie(authSessionService.rotate(refreshToken, userAgent));
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
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Si el email existe, vas a recibir un link para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request);
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida correctamente."));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthSession session) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", session.refreshToken())
                .httpOnly(true).secure(false).sameSite("Lax").path("/api/auth")
                .maxAge(Duration.ofDays(30)).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(session.response());
    }
