package com.daii.backdaii.domain.cobro;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.enums.TipoEspacio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarifarioTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 19);

    private static Map<TipoEspacio, BigDecimal> tarifas() {
        Map<TipoEspacio, BigDecimal> tarifas = new EnumMap<>(TipoEspacio.class);
        tarifas.put(TipoEspacio.CUBICULO, new BigDecimal("3500"));
        tarifas.put(TipoEspacio.SALA_REUNIONES, new BigDecimal("9000"));
        tarifas.put(TipoEspacio.SALA_PRESENTACIONES, new BigDecimal("14000"));
        tarifas.put(TipoEspacio.SALA_CONFERENCIAS, new BigDecimal("22000"));
        return tarifas;
    }

    private static Franja franja(int h1, int m1, int h2, int m2) {
        return new Franja(DIA.atTime(h1, m1), DIA.atTime(h2, m2));
    }

    private final Tarifario tarifario = new Tarifario(tarifas());

    @Test
    void cobraLaTarifaPorHoraDelTipoDeEspacio() {
        assertThat(tarifario.monto(TipoEspacio.CUBICULO, franja(9, 0, 11, 0))).isEqualByComparingTo("7000");
        assertThat(tarifario.monto(TipoEspacio.SALA_CONFERENCIAS, franja(9, 0, 10, 0))).isEqualByComparingTo("22000");
    }

    @Test
    void prorrateaPorMinutoConDosDecimales() {
        assertThat(tarifario.monto(TipoEspacio.CUBICULO, franja(9, 0, 10, 30))).isEqualTo(new BigDecimal("5250.00"));
        assertThat(tarifario.monto(TipoEspacio.SALA_REUNIONES, franja(9, 0, 9, 20))).isEqualTo(new BigDecimal("3000.00"));
    }

    @Test
    void noArrancaSiFaltaLaTarifaDeUnTipo() {
        Map<TipoEspacio, BigDecimal> incompletas = tarifas();
        incompletas.remove(TipoEspacio.SALA_PRESENTACIONES);

        assertThatThrownBy(() -> new Tarifario(incompletas))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SALA_PRESENTACIONES");
    }
}
