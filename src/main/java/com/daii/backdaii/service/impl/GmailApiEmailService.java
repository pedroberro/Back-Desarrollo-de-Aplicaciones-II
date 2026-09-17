package com.daii.backdaii.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

/**
 * Sends through the Gmail API over HTTPS (port 443), which hosts that block SMTP still allow.
 * Authenticates with OAuth2: a long-lived refresh token, granted once by the sending account,
 * is exchanged for short-lived access tokens that are reused until they are about to expire.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "gmail-api")
public class GmailApiEmailService extends AbstractEmailService {

    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {
    }

    private final RestClient http;
    private final Clock clock;

    @Value("${app.mail.gmail.client-id:}")
    private String clientId;

    @Value("${app.mail.gmail.client-secret:}")
    private String clientSecret;

    @Value("${app.mail.gmail.refresh-token:}")
    private String refreshToken;

    private String accessToken;
    private Instant accessTokenVence = Instant.EPOCH;

    // Two constructors (the other one is for tests): Spring must be told which to use.
    @Autowired
    public GmailApiEmailService(RestClient.Builder builder) {
        this(builder, Clock.systemUTC());
    }

    GmailApiEmailService(RestClient.Builder builder, Clock clock) {
        this.http = builder.build();
        this.clock = clock;
    }

    @Override
    protected boolean configurado() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();
    }

    @PostConstruct
    void avisarSiNoHayMail() {
        if (configurado()) {
            log.info("[ciclo de vida] GmailApiEmailService iniciado: envío de mails por la API de Gmail");
        } else {
            log.warn("Faltan GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET o GMAIL_REFRESH_TOKEN: no se envían mails, los links se escriben en este log.");
        }
    }

    @Override
    protected void enviarTexto(String to, String asunto, String texto) throws Exception {
        http.post()
                .uri(SEND_URL)
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", mensajeCodificado(to, asunto, texto)))
                .retrieve()
                .toBodilessEntity();
    }

    /** The API takes the whole RFC 822 message, base64url-encoded. */
    String mensajeCodificado(String to, String asunto, String texto) throws Exception {
        MimeMessage mensaje = new MimeMessage(Session.getInstance(new Properties()));
        mensaje.setFrom(new InternetAddress(from));
        mensaje.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        mensaje.setSubject(asunto, StandardCharsets.UTF_8.name());
        mensaje.setText(texto, StandardCharsets.UTF_8.name());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        mensaje.writeTo(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes.toByteArray());
    }

    // Synchronized: two mails sent at once must not both refresh the token.
    private synchronized String accessToken() {
        if (accessToken == null || clock.instant().isAfter(accessTokenVence)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("refresh_token", refreshToken);
            form.add("grant_type", "refresh_token");

            TokenResponse token = http.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            accessToken = token.accessToken();
            // One minute of margin, so a token never expires in the middle of a send.
            accessTokenVence = clock.instant().plusSeconds(token.expiresIn() - 60);
        }
        return accessToken;
    }
}
