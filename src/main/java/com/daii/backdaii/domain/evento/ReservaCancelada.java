package com.daii.backdaii.domain.evento;

import java.math.BigDecimal;

/**
 * A booking stopped taking its slot: cancelled by the customer or an administrator, deleted, or
 * swept by a block. Listeners tell the customer after commit, refund included.
 *
 * @param cobrado     what had been charged; null when nothing was (pending, or charging is off)
 * @param reintegrado what goes back to the card; zero when nothing does
 */
public record ReservaCancelada(
        String codigoReserva,
        String descripcionReserva,
        String email,
        String nombre,
        Motivo motivo,
        String detalle,
        BigDecimal cobrado,
        BigDecimal reintegrado,
        int porcentaje) {

    public enum Motivo { CLIENTE, ADMINISTRADOR, ELIMINADA, BLOQUEO }
}
