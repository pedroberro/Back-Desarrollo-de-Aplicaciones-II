package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:Planta Libre <plantalibre.ar@gmail.com>}")
    private String from;

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.info("[dev] Enlace de recuperación para {}: {}", to, resetLink);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Restablecé tu contraseña - Planta Libre");
        message.setText("Para restablecer tu contraseña, entrá al siguiente enlace:\n\n" + resetLink);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("No se pudo enviar el correo a {}", to, e);
        }
    }
}