package com.daii.backdaii.domain.cobro;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.enums.TipoEspacio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/** Price of a booking: hourly rate of the space kind times the length of the slot, prorated by minute. */
public class Tarifario {

    private static final BigDecimal MINUTOS_POR_HORA = BigDecimal.valueOf(60);

    private final Map<TipoEspacio, BigDecimal> porHora;

    public Tarifario(Map<TipoEspacio, BigDecimal> porHora) {
        this.porHora = new EnumMap<>(TipoEspacio.class);
        this.porHora.putAll(porHora);
        for (TipoEspacio tipo : TipoEspacio.values()) {
            if (!this.porHora.containsKey(tipo)) {
                throw new IllegalStateException("Falta la tarifa por hora de " + tipo);
            }
        }
    }

    public BigDecimal porHora(TipoEspacio tipo) {
        return porHora.get(tipo);
    }

    public BigDecimal monto(TipoEspacio tipo, Franja franja) {
        BigDecimal minutos = BigDecimal.valueOf(franja.duracion().toMinutes());
        return porHora.get(tipo).multiply(minutos).divide(MINUTOS_POR_HORA, 2, RoundingMode.HALF_UP);
    }
}
