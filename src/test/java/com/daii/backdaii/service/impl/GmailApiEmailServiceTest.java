package com.daii.backdaii.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiEmailServiceTest {

    private MockRestServiceServer google;
    private GmailApiEmailService servicio;

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
        servicio = new GmailApiEmailService(builder);
        ReflectionTestUtils.setField(servicio, "clientId", "id");
        ReflectionTestUtils.setField(servicio, "clientSecret", "secreto");
        ReflectionTestUtils.setField(servicio, "refreshToken", "refresh");
        ReflectionTestUtils.setField(servicio, "from", "Planta Libre <no.reply.planta.libre@gmail.com>");
    }

    @Test
    void renuevaElTokenUnaVezYLoReusaEnLosEnvios() throws Exception {
        google.expect(requestTo(GmailApiEmailService.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "refresh_token", "refresh_token", "refresh")))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        google.expect(requestTo(GmailApiEmailService.SEND_URL))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess());
        google.expect(requestTo(GmailApiEmailService.SEND_URL))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess());

        servicio.enviarTexto("ana@example.com", "Asunto", "Hola");
        servicio.enviarTexto("ana@example.com", "Asunto", "Hola");

        google.verify();
    }

    @Test
    void armaUnMensajeConRemitenteDestinatarioYAsuntoEnUtf8() throws Exception {
        String raw = servicio.mensajeCodificado("ana@example.com", "Confirmá tu email", "Hola Ana");
        String mensaje = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);

        assertThat(mensaje)
                .contains("From: Planta Libre <no.reply.planta.libre@gmail.com>")
                .contains("To: ana@example.com")
                .contains("Subject: =?UTF-8?")
                .contains("Hola Ana");
    }
}
