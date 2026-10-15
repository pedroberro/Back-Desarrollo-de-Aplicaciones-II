package com.daii.backdaii.domain.evento;

import java.math.BigDecimal;

/** A booking was charged (aprobado) or could not be charged and was cancelled. */
public record CobroResuelto(
        Long reservaId,
        String codigoReserva,
        String descripcionReserva,
        String email,
        String nombre,
        BigDecimal monto,
        String ultimos4,
        boolean aprobado,
        String motivoRechazo) {
}
