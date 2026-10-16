package com.daii.backdaii.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Have I Been Pwned's Pwned Passwords range API, with k-anonymity: only the first five hex
 * characters of the password's SHA-1 leave the server, and the match is done locally against the
 * returned suffixes. Padding hides the real size of the answer. If the service is down the check
 * is skipped (logged): a third party outage must not block every sign-up.
 */
@Component
public class PwnedPasswordsCliente implements ConsultaFiltraciones {

    private static final Logger log = LoggerFactory.getLogger(PwnedPasswordsCliente.class);
    static final String URL = "https://api.pwnedpasswords.com/range/";

    private final RestClient http;

    public PwnedPasswordsCliente(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(3000);
        tiempos.setReadTimeout(3000);
        this.http = builder.requestFactory(tiempos).build();
    }

    @Override
    public boolean filtrada(String password) {
        String hash = sha1(password);
        String prefijo = hash.substring(0, 5);
        String sufijo = hash.substring(5);
        try {
            String respuesta = http.get().uri(URL + prefijo)
                    .header("Add-Padding", "true")
                    .header("User-Agent", "PlantaLibre")
                    .retrieve().body(String.class);
            if (respuesta == null) {
                return false;
            }
            for (String linea : respuesta.split("\r?\n")) {
                String[] partes = linea.split(":");
                // Padding lines have a count of 0.
                if (partes.length == 2 && partes[0].equalsIgnoreCase(sufijo) && Integer.parseInt(partes[1].trim()) > 0) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("No se pudo consultar Pwned Passwords ({}): se omite la verificación de filtraciones", e.getMessage());
            return false;
        }
    }

    static String sha1(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
