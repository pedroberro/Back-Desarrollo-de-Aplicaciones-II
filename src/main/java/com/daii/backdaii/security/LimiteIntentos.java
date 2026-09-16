package com.daii.backdaii.security;

import java.time.Duration;

/** Public actions that are capped per client IP, with how many calls each allows per window. */
public enum LimiteIntentos {
    // Reveals whether an account exists: capped so bulk lookups are impractical.
    EMAIL_DISPONIBLE(20, Duration.ofMinutes(1)),
    // Each call can send a mail: capped to avoid flooding someone's inbox.
    RECUPERAR_PASSWORD(5, Duration.ofMinutes(15)),
    REENVIAR_CONFIRMACION(5, Duration.ofMinutes(15));

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
