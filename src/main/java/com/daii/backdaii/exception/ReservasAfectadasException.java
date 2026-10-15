package com.daii.backdaii.exception;

import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaAfectada;
import lombok.Getter;

import java.util.List;

/**
 * A block falls on bookings and the administrator did not say what to do with them. Carries the
 * bookings, so the client can show them and retry with {@code cancelarReservas}.
 */
@Getter
public class ReservasAfectadasException extends ConflictException {

    public static final String CODE = "RESERVAS_AFECTADAS";

    private final List<ReservaAfectada> reservas;

    public ReservasAfectadasException(String message, List<ReservaAfectada> reservas) {
        super(message, CODE);
        this.reservas = reservas;
    }
}
