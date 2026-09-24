package com.daii.backdaii.domain.disponibilidad;

import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasReservaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 8, 26);
    private static final Horario HORARIO = new Horario(LocalTime.of(8, 0), LocalTime.of(20, 0));
    private static final LocalDateTime AHORA = DIA.atTime(9, 40);

    private final ReglasReserva reglas = new ReglasReserva();

    private static Franja franja(int h1, int m1, int h2, int m2) {
        return new Franja(DIA.atTime(h1, m1), DIA.atTime(h2, m2));
    }

    private void rechaza(Franja franja, String codigo) {
        assertThatThrownBy(() -> reglas.validar(franja, HORARIO, AHORA))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(codigo);
    }

    @Test
    void aceptaUnaFranjaValida() {
        assertThatCode(() -> reglas.validar(franja(10, 0, 11, 30), HORARIO, AHORA)).doesNotThrowAnyException();
    }

    @Test
    void exigeBloquesDeMediaHora() {
        rechaza(franja(10, 15, 11, 0), "FRANJA_INVALIDA");
    }

    @Test
    void exigeEntreMediaHoraYOchoHoras() {
        rechaza(franja(10, 0, 19, 0), "FRANJA_INVALIDA");
    }

    @Test
    void exigeQuedarDentroDelHorarioDelEdificio() {
        rechaza(franja(19, 0, 20, 30), "FUERA_DE_HORARIO");
        rechaza(new Franja(DIA.plusDays(1).atTime(7, 30), DIA.plusDays(1).atTime(9, 0)), "FUERA_DE_HORARIO");
    }

    @Test
    void losEdificiosNoAbrenElFinDeSemana() {
        LocalDate sabado = LocalDate.of(2026, 8, 29);
        rechaza(new Franja(sabado.atTime(10, 0), sabado.atTime(11, 0)), "FUERA_DE_HORARIO");
    }

    @Test
    void noPermiteReservarUnaFranjaQueYaEmpezo() {
        rechaza(franja(9, 30, 10, 30), "FRANJA_PASADA");
    }

    @Test
    void soloSeCancelaLoActivoQueTodaviaNoEmpezo() {
        Reserva futura = Reserva.builder().estado(EstadoReserva.CONFIRMADA)
                .inicio(DIA.atTime(10, 0)).fin(DIA.atTime(11, 0)).build();
        Reserva enCurso = Reserva.builder().estado(EstadoReserva.PENDIENTE)
                .inicio(DIA.atTime(9, 0)).fin(DIA.atTime(11, 0)).build();
        Reserva cancelada = Reserva.builder().estado(EstadoReserva.CANCELADA)
                .inicio(DIA.atTime(12, 0)).fin(DIA.atTime(13, 0)).build();

        assertThat(reglas.cancelable(futura, AHORA)).isTrue();
        assertThat(reglas.cancelable(enCurso, AHORA)).isFalse();
        assertThat(reglas.cancelable(cancelada, AHORA)).isFalse();
    }

    @Test
    void unaReservaActivaQueTerminoSeMuestraFinalizada() {
        Reserva pasada = Reserva.builder().estado(EstadoReserva.CONFIRMADA)
                .inicio(DIA.atTime(8, 0)).fin(DIA.atTime(9, 0)).build();

        assertThat(pasada.estadoEn(AHORA)).isEqualTo(EstadoReserva.FINALIZADA);
        assertThat(pasada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }
}
