package com.daii.backdaii.service;

import com.daii.backdaii.domain.entity.EmailVerificationToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.EmailVerificationTokenRepository;
import com.daii.backdaii.exception.ApiException;
import com.daii.backdaii.security.OpaqueTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokenRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final JavaMailSender mailSender;

    @Value("${app.auth.verify-email-url:http://localhost:5173/verify-email}")
    private String verifyEmailUrl;

    @Value("${spring.mail.username:}")
    private String from;

    @Transactional
    public void sendVerification(Usuario usuario) {
        String rawToken = opaqueTokenService.generate();
        tokenRepository.save(EmailVerificationToken.builder()
                .usuario(usuario)
                .tokenHash(opaqueTokenService.hash(rawToken))
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());

        String separator = verifyEmailUrl.contains("?") ? "&" : "?";
        String link = verifyEmailUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(usuario.getEmail());
        message.setSubject("Confirmá tu cuenta - Planta Libre");
        message.setText("Hola " + usuario.getNombre() + ",\n\nConfirmá tu email para poder iniciar sesión:\n"
                + link + "\n\nEl enlace vence en 24 horas.");
        mailSender.send(message);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHashAndUsedAtIsNull(opaqueTokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN", "El enlace no es válido"));
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPIRED_VERIFICATION_TOKEN", "El enlace expiró");
        }
        token.getUsuario().setEmailVerified(true);
        token.setUsedAt(LocalDateTime.now());
    }
}
