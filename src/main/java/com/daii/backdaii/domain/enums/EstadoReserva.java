package com.daii.backdaii.domain.enums;

import java.util.List;

/**
 * State of a booking, as the State pattern: each state knows which states it may move to, and
 * {@link com.daii.backdaii.domain.entity.Reserva#cambiarEstado} is the only way to change it.
 *
 * <pre>
 *   PENDIENTE ──► CONFIRMADA ──► CANCELADA
 *       └─────────────────────────►┘
 * </pre>
 *
 * CANCELADA and FINALIZADA are final. Stored states are PENDIENTE, CONFIRMADA and CANCELADA.
 * FINALIZADA is never stored: an active booking whose end already passed is reported as finished,
 * so no job has to update rows.
 */
public enum EstadoReserva {
    /** Waiting for the charge. */
    PENDIENTE("pendiente") {
        @Override
        public boolean puedePasarA(EstadoReserva destino) {
            return destino == CONFIRMADA || destino == CANCELADA;
        }
    },
    /** Paid. */
    CONFIRMADA("confirmada") {
        @Override
        public boolean puedePasarA(EstadoReserva destino) {
            return destino == CANCELADA;
        }
    },
    /** Final: the slot is free again and any refund is already done. */
    CANCELADA("cancelada") {
        @Override
        public boolean puedePasarA(EstadoReserva destino) {
            return false;
        }
    },
    /** Final: derived when an active booking's end already passed. */
    FINALIZADA("finalizada") {
        @Override
        public boolean puedePasarA(EstadoReserva destino) {
            return false;
        }
    };

    /** States that take the slot: the availability engine only looks at these. */
    public static final List<EstadoReserva> ACTIVOS = List.of(PENDIENTE, CONFIRMADA);

    private final String etiqueta;

    EstadoReserva(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public abstract boolean puedePasarA(EstadoReserva destino);

    /** @throws TransicionInvalidaException when this state may not move to {@code destino} */
    public void validarPaso(EstadoReserva destino) {
        if (!puedePasarA(destino)) {
            throw new TransicionInvalidaException(this, destino);
        }
    }

    public boolean ocupa() {
        return this == PENDIENTE || this == CONFIRMADA;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
