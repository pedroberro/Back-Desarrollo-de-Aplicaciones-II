package com.daii.backdaii.security;

import java.time.Duration;

/** Public actions that are capped per client (IP or account), with how many calls each allows per window. */
public enum LimiteIntentos {
    // Reveals whether an account exists: capped so bulk lookups are impractical.
    EMAIL_DISPONIBLE(20, Duration.ofMinutes(1)),
    // Each call can send a mail: capped to avoid flooding someone's inbox.
    RECUPERAR_PASSWORD(5, Duration.ofMinutes(15)),
    REENVIAR_CONFIRMACION(5, Duration.ofMinutes(15)),
    // Failed sign-ins per account: stops guessing a password, whatever IP the guesses come from.
    LOGIN_FALLIDO(10, Duration.ofMinutes(15)),
    // Wrong second-factor codes per account: six digits fall to brute force without it.
    SEGUNDO_FACTOR_FALLIDO(5, Duration.ofMinutes(15)),
    // Every API call per client (signed-in user, or IP when anonymous).
    API_GENERAL(200, Duration.ofMinutes(1)),
    // Bookings created per user: each one locks a space and queues a charge.
    CREAR_RESERVA(10, Duration.ofMinutes(1));

    private final int maximo;
    private final Duration ventana;

    LimiteIntentos(int maximo, Duration ventana) {
        this.maximo = maximo;
        this.ventana = ventana;
    }

    public int getMaximo() {
        return maximo;
    }

    public Duration getVentana() {
        return ventana;
    }
}
