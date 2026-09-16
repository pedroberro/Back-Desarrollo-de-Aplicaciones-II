package com.daii.backdaii.controller;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.AuthSession;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
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
    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(usuarioService.getMe(user.getUsername()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UsuarioDTO> updateMe(@AuthenticationPrincipal UserDetails user,
                                               @Valid @RequestBody com.daii.backdaii.dto.usuario.UpdateMeRequest request) {
        return ResponseEntity.ok(usuarioService.updateMe(user.getUsername(), request));
    }

    @GetMapping("/email-available")
    public Map<String, Boolean> emailAvailable(@RequestParam String email) {
        return Map.of("available", authService.emailAvailable(email));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok(Map.of("message", "Email verificado. Ya podés iniciar sesión."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.request(request.getEmail());
        // No revela si un email está registrado.
        return ResponseEntity.ok(Map.of("message", "Si existe una cuenta asociada, enviamos un enlace de recuperación."));
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
}
