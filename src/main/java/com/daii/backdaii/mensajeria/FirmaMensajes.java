package com.daii.backdaii.mensajeria;

import com.daii.backdaii.security.Secretos;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 over the message id and the body (SEC-08). The producer signs, the consumer checks:
 * a message altered in transit, or forged by someone with write access to the broker but without
 * the secret, is rejected before it can trigger a charge.
 */
public class FirmaMensajes {

    public static final String CABECERA = "x-firma";
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec clave;

    public FirmaMensajes(String secreto) {
        this.clave = new SecretKeySpec(Secretos.requerido("MENSAJERIA_FIRMA_SECRETO", secreto)
                .getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    public String firmar(String mensajeId, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            mac.update((mensajeId == null ? "" : mensajeId).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return Base64.getEncoder().encodeToString(mac.doFinal(cuerpo));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant time: the comparison does not reveal how much of a forged signature was right. */
    public boolean valida(String mensajeId, byte[] cuerpo, Object firma) {
        return firma instanceof String texto && MessageDigest.isEqual(
                firmar(mensajeId, cuerpo).getBytes(StandardCharsets.US_ASCII), texto.getBytes(StandardCharsets.US_ASCII));
    }
}
