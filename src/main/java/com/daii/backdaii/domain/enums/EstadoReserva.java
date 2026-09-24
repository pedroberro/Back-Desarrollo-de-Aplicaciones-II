package com.daii.backdaii.domain.enums;

import java.util.List;

/**
 * Stored states are PENDIENTE, CONFIRMADA and CANCELADA. FINALIZADA is never stored: an active
 * booking whose end already passed is reported as finished, so no job has to update rows.
 */
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    CANCELADA,
    FINALIZADA;

    /** States that take the slot: the availability engine only looks at these. */
    public static final List<EstadoReserva> ACTIVOS = List.of(PENDIENTE, CONFIRMADA);

    public boolean ocupa() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
