package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;

/**
 * What every mail sender shares: the texts of each mail and the fallback of printing the
 * links when sending is not configured. Subclasses only decide how a finished mail leaves
 * the app (template method), and the active one is picked by {@code app.mail.provider} (strategy).
 */
public abstract class AbstractEmailService implements EmailService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${app.mail.from:Planta Libre <no.reply.planta.libre@gmail.com>}")
    protected String from;

    // Development aid: also print the links, so flows can be tested without opening the inbox.
    @Value("${app.mail.log-links:false}")
    private boolean logLinks;

    /** Whether the credentials this sender needs are present. */
    protected abstract boolean configurado();

    /** Sends a plain-text mail; failures are logged, never thrown to the caller. */
    protected abstract void enviarTexto(String to, String asunto, String texto) throws Exception;

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
        try {
            enviarTexto(to, asunto, texto);
            log.info("Mail \"{}\" enviado a {}", asunto, to);
        } catch (Exception e) {
            log.error("No se pudo enviar el mail \"{}\" a {}", asunto, to, e);
        }
    }
}
