package com.daii.backdaii.service;

import com.daii.backdaii.domain.entity.PasswordResetToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.PasswordResetTokenRepository;
import com.daii.backdaii.domain.repository.RefreshTokenRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.exception.ApiException;
import com.daii.backdaii.security.OpaqueTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.auth.reset-expiration-minutes:30}")
    private long resetExpirationMinutes;

    @Value("${app.auth.reset-url:http://localhost:5173/reset-password}")
    private String resetUrl;

    @Value("${spring.mail.username:}")
    private String from;

    @Transactional
    public void request(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String rawToken = opaqueTokenService.generate();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .usuario(usuario)
                    .tokenHash(opaqueTokenService.hash(rawToken))
                    .expiresAt(LocalDateTime.now().plusMinutes(resetExpirationMinutes))
                    .build());
            sendEmail(usuario, rawToken);
        });
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(opaqueTokenService.hash(request.getToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "El enlace no es válido"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPIRED_RESET_TOKEN", "El enlace expiró");
        }

        Usuario usuario = resetToken.getUsuario();
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        resetToken.setUsedAt(LocalDateTime.now());
        refreshTokenRepository.deleteByUsuarioId(usuario.getId());
    }

    private void sendEmail(Usuario usuario, String rawToken) {
        String separator = resetUrl.contains("?") ? "&" : "?";
        String link = resetUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(usuario.getEmail());
        message.setSubject("Restablecer contraseña - Planta Libre");
        message.setText("Recibimos una solicitud para restablecer tu contraseña.\n\n"
                + "Usá este enlace dentro de " + resetExpirationMinutes + " minutos:\n" + link
                + "\n\nSi no solicitaste este cambio, ignorá este correo.");
        mailSender.send(message);
    }
}
