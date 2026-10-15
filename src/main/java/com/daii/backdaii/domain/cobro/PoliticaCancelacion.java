package com.daii.backdaii.domain.cobro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * What a cancellation gives back. The booking is charged when it is made; how much is refunded
 * depends on who cancels and how early:
 * <ul>
 *   <li>the customer, 24 h or more before the start: everything;</li>
 *   <li>the customer, between 24 h and 2 h before: half;</li>
 *   <li>the customer, less than 2 h before: nothing (the slot is freed anyway);</li>
 *   <li>an administrator (cancel, delete, or a block over the booking): everything, always.</li>
 * </ul>
 * After the start a booking cannot be cancelled (see ReglasReserva).
 */
public final class PoliticaCancelacion {

    public static final Duration SIN_CARGO = Duration.ofHours(24);
    public static final Duration CON_RETENCION = Duration.ofHours(2);
    public static final int REINTEGRO_PARCIAL = 50;

    public enum Quien { CLIENTE, ADMINISTRADOR }

    private PoliticaCancelacion() {
    }

    public static int porcentajeReintegro(LocalDateTime inicio, LocalDateTime ahora, Quien quien) {
        if (quien == Quien.ADMINISTRADOR) {
            return 100;
        }
        Duration anticipacion = Duration.between(ahora, inicio);
        if (anticipacion.compareTo(SIN_CARGO) >= 0) {
            return 100;
        }
        return anticipacion.compareTo(CON_RETENCION) >= 0 ? REINTEGRO_PARCIAL : 0;
    }

    public static BigDecimal reintegro(BigDecimal cobrado, int porcentaje) {
        return cobrado.multiply(BigDecimal.valueOf(porcentaje)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
