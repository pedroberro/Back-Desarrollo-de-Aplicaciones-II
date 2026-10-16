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
        limitador = new LimitadorDeIntentos(new AlmacenIntentosEnMemoria(reloj));
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
    void elBloqueoSobreviveAUnReinicioDelLimitador() {
        // SEC-04: the counters live in the store, not in the limiter. A new limiter over the same
        // store (an app restart, or another instance) still sees the account locked.
        AlmacenIntentosEnMemoria almacen = new AlmacenIntentosEnMemoria(reloj);
        LimitadorDeIntentos antes = new LimitadorDeIntentos(almacen);
        for (int i = 0; i < LimiteIntentos.LOGIN_FALLIDO.getMaximo(); i++) {
            antes.permitir(LimiteIntentos.LOGIN_FALLIDO, "ana@example.com");
        }
        LimitadorDeIntentos despues = new LimitadorDeIntentos(almacen);
        assertThat(despues.agotado(LimiteIntentos.LOGIN_FALLIDO, "ana@example.com")).isTrue();
    }

    @Test
    void elLoginSeTrabaRecienDespuesDeDiezFallosYSeLiberaAlAcertar() {
        String cuenta = "ana@example.com";
        for (int i = 0; i < 9; i++) {
            limitador.permitir(LimiteIntentos.LOGIN_FALLIDO, cuenta);
        }
        assertThat(limitador.agotado(LimiteIntentos.LOGIN_FALLIDO, cuenta)).isFalse();

        limitador.permitir(LimiteIntentos.LOGIN_FALLIDO, cuenta);
        assertThat(limitador.agotado(LimiteIntentos.LOGIN_FALLIDO, cuenta)).isTrue();

        limitador.reiniciar(LimiteIntentos.LOGIN_FALLIDO, cuenta);
        assertThat(limitador.agotado(LimiteIntentos.LOGIN_FALLIDO, cuenta)).isFalse();
    }

    @Test
    void elBloqueoDelLoginVenceConLaVentana() {
        for (int i = 0; i < 10; i++) {
            limitador.permitir(LimiteIntentos.LOGIN_FALLIDO, "ana@example.com");
        }
        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(limitador.agotado(LimiteIntentos.LOGIN_FALLIDO, "ana@example.com")).isFalse();
    }
}
