package com.daii.backdaii.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends through an SMTP server (Gmail on port 587). Used locally; Render's free plan blocks SMTP. */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
@RequiredArgsConstructor
public class SmtpEmailService extends AbstractEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    protected boolean configurado() {
        return !mailUsername.isBlank() && !mailPassword.isBlank();
    }

    // Stateless: its fields are configuration fixed at startup, never per-request data.
    // The container still owns its life cycle and calls this once the values are injected.
    @PostConstruct
    void avisarSiNoHayMail() {
        if (configurado()) {
            log.info("[ciclo de vida] SmtpEmailService iniciado: envío de mails activo desde {}", mailUsername);
        } else {
            log.warn("MAIL_PASSWORD no está configurada: no se envían mails, los links se escriben en este log. Ver back/.env.example");
        }
    }

    @Override
    protected void enviarTexto(String to, String asunto, String texto) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(asunto);
        message.setText(texto);
        mailSender.send(message);
    }
}
