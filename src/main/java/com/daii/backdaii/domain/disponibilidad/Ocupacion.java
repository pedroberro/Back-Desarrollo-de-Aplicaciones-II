package com.daii.backdaii.domain.disponibilidad;

import java.time.LocalDateTime;

/**
 * Anything that takes a space for a while. For the engine a booking and a block are the same
 * thing; the type only decides the colour and which one wins when both overlap a slot.
 */
public record Ocupacion(Long espacioId, LocalDateTime inicio, LocalDateTime fin, Tipo tipo, Long referenciaId) {

    public enum Tipo { RESERVA, BLOQUEO }

    /** Used by the repositories' constructor expressions, where the type comes as a literal. */
    public Ocupacion(Long espacioId, LocalDateTime inicio, LocalDateTime fin, String tipo, Long referenciaId) {
        this(espacioId, inicio, fin, Tipo.valueOf(tipo), referenciaId);
    }

    public static Ocupacion reserva(Long espacioId, LocalDateTime inicio, LocalDateTime fin, Long reservaId) {
        return new Ocupacion(espacioId, inicio, fin, Tipo.RESERVA, reservaId);
    }

    public static Ocupacion bloqueo(Long espacioId, LocalDateTime inicio, LocalDateTime fin, Long bloqueoId) {
        return new Ocupacion(espacioId, inicio, fin, Tipo.BLOQUEO, bloqueoId);
    }

    public Franja franja() {
        return new Franja(inicio, fin);
    }

    public boolean esReserva(Long reservaId) {
        return tipo == Tipo.RESERVA && referenciaId != null && referenciaId.equals(reservaId);
    }
}
