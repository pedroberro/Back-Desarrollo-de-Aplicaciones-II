package com.daii.backdaii.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Time-based one-time passwords (RFC 6238 over RFC 4226): HMAC-SHA1, 6 digits, 30-second steps,
 * the format every authenticator app reads. Pure: the caller keeps the secret and the last step.
 */
public final class Totp {

    public static final int PASO_SEGUNDOS = 30;
    private static final int DIGITOS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160 random bits in base32, as RFC 4226 recommends. */
    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static String uri(String emisor, String cuenta, String secreto) {
        String etiqueta = URLEncoder.encode(emisor + ":" + cuenta, StandardCharsets.UTF_8).replace("+", "%20");
        String emisorUri = URLEncoder.encode(emisor, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto + "&issuer=" + emisorUri
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    public static long paso(Instant instante) {
        return instante.getEpochSecond() / PASO_SEGUNDOS;
    }

    public static String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(desdeBase32(secreto), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int desplazamiento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[desplazamiento] & 0x7f) << 24) | ((hash[desplazamiento + 1] & 0xff) << 16)
                    | ((hash[desplazamiento + 2] & 0xff) << 8) | (hash[desplazamiento + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The step whose code matches, accepting one step of clock drift on each side, and never a
     * step at or before {@code ultimoPasoUsado} (no replay). Empty when the code does not match.
     */
    public static OptionalLong verificar(String secreto, String codigo, Instant ahora, Long ultimoPasoUsado) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long actual = paso(ahora);
        for (long p = actual - 1; p <= actual + 1; p++) {
            if ((ultimoPasoUsado == null || p > ultimoPasoUsado) && iguales(codigo(secreto, p), codigo)) {
                return OptionalLong.of(p);
            }
        }
        return OptionalLong.empty();
    }

    /** Constant time, so the comparison does not leak how many digits matched. */
    private static boolean iguales(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    static String base32(byte[] datos) {
        StringBuilder salida = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                salida.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return salida.toString();
    }

    static byte[] desdeBase32(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto base32 inválido");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
