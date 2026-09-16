package com.daii.backdaii.controller;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UsuarioDTO> register(@Valid @RequestBody RegistroRequest request) {
        UsuarioDTO usuario = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
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
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Si el email existe, vas a recibir un link para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }
}