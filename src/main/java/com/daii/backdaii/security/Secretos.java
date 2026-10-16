package com.daii.backdaii.security;

import java.util.List;
import java.util.Locale;

/**
 * Fail-fast checks for secrets read from the environment. A missing or weak secret stops the
 * application on startup instead of running with a guessable one; the message names the variable,
 * never the value.
 */
public final class Secretos {

    /** 256 bits written as text: the minimum for an HMAC-SHA256 or AES-256 key source. */
    public static final int LARGO_MINIMO = 32;

    private static final List<String> DEBILES = List.of("default", "changeme", "secret", "password", "example", "1234");

    private Secretos() {
    }

    public static String requerido(String variable, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + variable + ": la aplicación no arranca sin ella.");
        }
        String limpio = valor.trim();
        if (limpio.length() < LARGO_MINIMO) {
            throw new IllegalStateException("La variable " + variable + " es demasiado corta: tiene que tener al menos "
                    + LARGO_MINIMO + " caracteres aleatorios.");
        }
        String minusculas = limpio.toLowerCase(Locale.ROOT);
        if (DEBILES.stream().anyMatch(minusculas::contains) || limpio.chars().distinct().count() < 10) {
            throw new IllegalStateException("La variable " + variable + " parece un valor de ejemplo o poco aleatorio: generá uno nuevo.");
        }
        return limpio;
    }
}
