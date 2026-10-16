package com.daii.backdaii.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Field-level encryption at rest (SEC-09): AES-256-GCM, a random 96-bit IV per value, and the key
 * version in front so keys can rotate. Stored as {@code enc:<version>:<base64(iv|ciphertext|tag)>}.
 *
 * <p>Keys come from the environment, never from the code: {@code CIFRADO_CLAVE_ACTUAL} with its
 * version {@code CIFRADO_VERSION_ACTUAL}, and the previous ones in {@code CIFRADO_CLAVES_ANTERIORES}
 * ("v1=...,v2=..."). To rotate: make the current key a previous one, set a new current key and a
 * new version, restart; {@link RecifradoCampos} re-encrypts every value with the new key.
 * Values without the {@code enc:} prefix are legacy plaintext and are read as they are.
 */
@Component
public class CifradoCampos {

    private static final String PREFIJO = "enc:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String versionActual;
    private final Map<String, SecretKeySpec> claves = new HashMap<>();

    public CifradoCampos(@Value("${app.cifrado.clave-actual:}") String claveActual,
                         @Value("${app.cifrado.version-actual:v1}") String versionActual,
                         @Value("${app.cifrado.claves-anteriores:}") String anteriores) {
        this.versionActual = versionActual.trim();
        claves.put(this.versionActual, derivar(Secretos.requerido("CIFRADO_CLAVE_ACTUAL", claveActual)));
        for (String par : anteriores.split(",")) {
            if (par.isBlank()) {
                continue;
            }
            String[] partes = par.trim().split("=", 2);
            if (partes.length != 2) {
                throw new IllegalStateException("CIFRADO_CLAVES_ANTERIORES tiene que tener la forma v1=clave,v2=clave");
            }
            claves.put(partes[0].trim(), derivar(Secretos.requerido("CIFRADO_CLAVES_ANTERIORES (" + partes[0].trim() + ")", partes[1])));
        }
    }

    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claves.get(versionActual), new GCMParameterSpec(TAG_BITS, iv));
            // The version is authenticated too: a value cannot be moved to another key version.
            cipher.updateAAD(versionActual.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + versionActual + ":" + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar un campo", e);
        }
    }

    public String descifrar(String guardado) {
        if (guardado == null || !guardado.startsWith(PREFIJO)) {
            return guardado;
        }
        String[] partes = guardado.split(":", 3);
        SecretKeySpec clave = claves.get(partes[1]);
        if (clave == null) {
            throw new IllegalStateException("Falta la clave de cifrado " + partes[1] + " en CIFRADO_CLAVES_ANTERIORES");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(partes[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, todo, 0, IV_BYTES));
            cipher.updateAAD(partes[1].getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(todo, IV_BYTES, todo.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Un campo cifrado no se pudo descifrar: dato alterado o clave equivocada", e);
        }
    }

    /** Plaintext, or encrypted with an older key: what the re-encryption pass looks for. */
    public boolean desactualizado(String guardado) {
        return guardado != null && !guardado.startsWith(PREFIJO + versionActual + ":");
    }

    public String versionActual() {
        return versionActual;
    }

    /** Any high-entropy text becomes a 256-bit AES key. */
    private static SecretKeySpec derivar(String secreto) {
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
