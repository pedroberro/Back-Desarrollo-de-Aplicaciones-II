package com.daii.backdaii.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorDeIntentosTest {

    /** Clock the test moves by hand, so windows can expire without waiting. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-09-18T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private RelojManual reloj;
    private LimitadorDeIntentos limitador;

    @BeforeEach
    void crear() {
        reloj = new RelojManual();
        limitador = new LimitadorDeIntentos(reloj);
        limitador.iniciar();
    }

    @AfterEach
    void destruir() {
        limitador.detener();
    }

    @Test
    void permiteHastaElMaximoYDespuesRechaza() {
        int maximo = LimiteIntentos.RECUPERAR_PASSWORD.getMaximo();
        for (int i = 0; i < maximo; i++) {
            assertThat(limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.1")).isTrue();
        }
        assertThat(limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.1")).isFalse();
    }

    @Test
    void guardaEstadoSeparadoPorClienteYPorAccion() {
        int maximo = LimiteIntentos.RECUPERAR_PASSWORD.getMaximo();
        for (int i = 0; i <= maximo; i++) {
            limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.1");
        }
        assertThat(limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.2")).isTrue();
        assertThat(limitador.permitir(LimiteIntentos.REENVIAR_CONFIRMACION, "10.0.0.1")).isTrue();
    }

    @Test
    void vuelveAPermitirCuandoVenceLaVentana() {
        int maximo = LimiteIntentos.EMAIL_DISPONIBLE.getMaximo();
        for (int i = 0; i <= maximo; i++) {
            limitador.permitir(LimiteIntentos.EMAIL_DISPONIBLE, "10.0.0.1");
        }
        reloj.avanzar(LimiteIntentos.EMAIL_DISPONIBLE.getVentana());
        assertThat(limitador.permitir(LimiteIntentos.EMAIL_DISPONIBLE, "10.0.0.1")).isTrue();
    }

    @Test
    void detenerDescartaElEstado() {
        int maximo = LimiteIntentos.RECUPERAR_PASSWORD.getMaximo();
        for (int i = 0; i <= maximo; i++) {
            limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.1");
        }
        limitador.detener();
        limitador.iniciar();
        assertThat(limitador.permitir(LimiteIntentos.RECUPERAR_PASSWORD, "10.0.0.1")).isTrue();
    }
}
