package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.EmailService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:Planta Libre <no.reply.planta.libre@gmail.com>}")
    private String from;

    // Development aid: also print the links, so flows can be tested without opening the inbox.
    @Value("${app.mail.log-links:false}")
    private boolean logLinks;

    private boolean configurado() {
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

    // Async so the response time does not reveal whether the account exists.
    @Async
    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        enviar(to, "Restablecé tu contraseña - Planta Libre", """
                Hola,

                Recibimos un pedido para restablecer la contraseña de tu cuenta de Planta Libre.
                Para elegir una nueva, entrá a este link:

                %s

                El link vence en 30 minutos y se puede usar una sola vez.
                Si no fuiste vos, ignorá este mensaje: tu contraseña sigue siendo la misma.
                """.formatted(resetLink), resetLink);
    }

    @Async
    @Override
    public void sendEmailConfirmation(String to, String nombre, String confirmLink) {
        enviar(to, "Confirmá tu email - Planta Libre", """
                Hola %s,

                Gracias por crear tu cuenta en Planta Libre.
                Para confirmar tu email y empezar a reservar, entrá a este link:

                %s

                El link se puede usar una sola vez.
                Si no creaste esta cuenta, ignorá este mensaje.
                """.formatted(nombre, confirmLink), confirmLink);
    }

    private void enviar(String to, String asunto, String texto, String link) {
        if (!configurado() || logLinks) {
            log.info("[dev] {} para {}: {}", asunto, to, link);
        }
        if (!configurado()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(asunto);
        message.setText(texto);

        try {
            mailSender.send(message);
            log.info("Mail \"{}\" enviado a {}", asunto, to);
        } catch (MailException e) {
            log.error("No se pudo enviar el mail \"{}\" a {}", asunto, to, e);
        }
    }
}
