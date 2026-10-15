package com.daii.backdaii.domain.cobro;

import com.daii.backdaii.domain.cobro.PoliticaCancelacion.Quien;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaCancelacionTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 20, 10, 0);

    private static int cliente(LocalDateTime ahora) {
        return PoliticaCancelacion.porcentajeReintegro(INICIO, ahora, Quien.CLIENTE);
    }

    @Test
    void conVeinticuatroHorasOMasDevuelveTodo() {
        assertThat(cliente(INICIO.minusHours(24))).isEqualTo(100);
        assertThat(cliente(INICIO.minusDays(5))).isEqualTo(100);
    }

    @Test
    void entreVeinticuatroYDosHorasDevuelveLaMitad() {
        assertThat(cliente(INICIO.minusHours(24).plusMinutes(1))).isEqualTo(50);
        assertThat(cliente(INICIO.minusHours(2))).isEqualTo(50);
    }

    @Test
    void conMenosDeDosHorasNoDevuelveNada() {
        assertThat(cliente(INICIO.minusHours(2).plusMinutes(1))).isZero();
        assertThat(cliente(INICIO.minusMinutes(10))).isZero();
    }

    @Test
    void siCancelaUnAdministradorSiempreDevuelveTodo() {
        assertThat(PoliticaCancelacion.porcentajeReintegro(INICIO, INICIO.minusMinutes(5), Quien.ADMINISTRADOR)).isEqualTo(100);
    }

    @Test
    void elReintegroSeRedondeaAlCentavo() {
        assertThat(PoliticaCancelacion.reintegro(new BigDecimal("5250.00"), 50)).isEqualTo(new BigDecimal("2625.00"));
        assertThat(PoliticaCancelacion.reintegro(new BigDecimal("1000.01"), 50)).isEqualTo(new BigDecimal("500.01"));
        assertThat(PoliticaCancelacion.reintegro(new BigDecimal("7000.00"), 0)).isEqualByComparingTo("0");
    }
}
