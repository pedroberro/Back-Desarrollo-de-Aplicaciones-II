package com.daii.backdaii.domain.disponibilidad;

import com.daii.backdaii.domain.enums.EstadoEspacio;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MotorDisponibilidadTest {

    private static final LocalDate DIA = LocalDate.of(2026, 8, 26);
    private static final Horario HORARIO = new Horario(LocalTime.of(8, 0), LocalTime.of(20, 0));
    private static final LocalDateTime TEMPRANO = DIA.atTime(7, 0);

    private final MotorDisponibilidad motor = new MotorDisponibilidad();

    private static LocalDateTime h(int hora, int minuto) {
        return DIA.atTime(hora, minuto);
    }

    private static Franja franja(int h1, int m1, int h2, int m2) {
        return new Franja(h(h1, m1), h(h2, m2));
    }

    private static Ocupacion reserva(long id, int h1, int m1, int h2, int m2) {
        return Ocupacion.reserva(1L, h(h1, m1), h(h2, m2), id);
    }

    @Test
    void franjasContiguasNoSeSuperponen() {
        List<Ocupacion> ocupaciones = List.of(reserva(1, 9, 0, 11, 0));

        assertThat(motor.conflicto(ocupaciones, franja(11, 0, 12, 0), null)).isEmpty();
        assertThat(motor.conflicto(ocupaciones, franja(8, 0, 9, 0), null)).isEmpty();
        assertThat(motor.conflicto(ocupaciones, franja(10, 30, 11, 30), null)).isPresent();
    }

    @Test
    void unaFranjaQueEnvuelveOtraChoca() {
        List<Ocupacion> ocupaciones = List.of(reserva(1, 10, 0, 10, 30));

        assertThat(motor.conflicto(ocupaciones, franja(9, 0, 12, 0), null)).isPresent();
    }

    @Test
    void editarUnaReservaNoChocaConsigoMisma() {
        List<Ocupacion> ocupaciones = List.of(reserva(7, 9, 0, 12, 0));

        assertThat(motor.conflicto(ocupaciones, franja(10, 0, 13, 0), 7L)).isEmpty();
        assertThat(motor.conflicto(ocupaciones, franja(10, 0, 13, 0), 8L)).isPresent();
    }

    @Test
    void unBloqueoGanaSobreUnaReservaAlPintarElEstado() {
        List<Ocupacion> ocupaciones = List.of(
                reserva(1, 9, 0, 10, 0),
                Ocupacion.bloqueo(1L, h(9, 30), h(11, 0), 3L));

        assertThat(motor.estado(ocupaciones, franja(9, 0, 11, 0))).isEqualTo(EstadoEspacio.BLOQUEADO);
        assertThat(motor.estado(ocupaciones, franja(11, 0, 12, 0))).isEqualTo(EstadoEspacio.LIBRE);
        assertThat(motor.estado(List.of(reserva(1, 9, 0, 10, 0)), franja(9, 0, 11, 0))).isEqualTo(EstadoEspacio.OCUPADO);
    }

    @Test
    void losHuecosSonLosRangosLibresMaximos() {
        List<Ocupacion> ocupaciones = List.of(
                reserva(1, 9, 0, 10, 0),
                reserva(2, 9, 30, 11, 0),
                reserva(3, 13, 0, 14, 0));

        assertThat(motor.huecos(ocupaciones, HORARIO.jornada(DIA))).containsExactly(
                franja(8, 0, 9, 0),
                franja(11, 0, 13, 0),
                franja(14, 0, 20, 0));
    }

    @Test
    void losHuecosDelDiaEmpiezanEnElProximoBloqueDeMediaHora() {
        List<Franja> huecos = motor.huecosDelDia(List.of(), DIA, HORARIO, h(10, 7));

        assertThat(huecos).containsExactly(franja(10, 30, 20, 0));
        assertThat(motor.huecosDelDia(List.of(), DIA, HORARIO, h(20, 0))).isEmpty();
    }

    @Test
    void elProximoHuecoSaltaAlDiaSiguienteSiHoyEstaTodoTomado() {
        List<Ocupacion> ocupaciones = List.of(Ocupacion.bloqueo(1L, h(8, 0), h(20, 0), 1L));

        Franja proximo = motor.proximoHueco(ocupaciones, TEMPRANO, HORARIO, Duration.ofMinutes(30), 14).orElseThrow();

        assertThat(proximo.inicio()).isEqualTo(DIA.plusDays(1).atTime(8, 0));
    }

    @Test
    void elFinDeSemanaNoHayHuecosYElProximoEsElLunes() {
        LocalDateTime viernesTarde = LocalDate.of(2026, 8, 28).atTime(19, 45);

        Franja proximo = motor.proximoHueco(List.of(), viernesTarde, HORARIO, Duration.ofMinutes(30), 14).orElseThrow();

        assertThat(motor.huecosDelDia(List.of(), LocalDate.of(2026, 8, 29), HORARIO, TEMPRANO)).isEmpty();
        assertThat(proximo.inicio()).isEqualTo(LocalDate.of(2026, 8, 31).atTime(8, 0));
    }

    @Test
    void losHuecosCercanosSeOrdenanPorCercaniaAlHorarioPedidoYDespuesPorHora() {
        List<Ocupacion> ocupaciones = List.of(
                reserva(1, 8, 0, 10, 0),
                reserva(2, 11, 30, 14, 0),
                reserva(3, 15, 30, 16, 0),
                reserva(4, 18, 0, 20, 0));

        List<Franja> cercanos = motor.huecosCercanos(ocupaciones, DIA, HORARIO, TEMPRANO,
                Duration.ofMinutes(90), h(10, 0), 3);

        // 10:00–11:30 contains the wanted start; 14:00–15:30 and 16:00–18:00 come next; nothing shorter than 90 min.
        assertThat(cercanos).containsExactly(franja(10, 0, 11, 30), franja(14, 0, 15, 30), franja(16, 0, 18, 0));
    }

    @Test
    void laSugerenciaBuscaLaPrimeraFranjaConAlgunEspacioLibre() {
        List<Ocupacion> espacioA = List.of(reserva(1, 9, 0, 11, 30));
        List<Ocupacion> espacioB = List.of(reserva(2, 9, 0, 12, 0));

        Franja sugerencia = motor.proximaFranjaConLibres(List.of(espacioA, espacioB), h(9, 30),
                Duration.ofHours(2), HORARIO).orElseThrow();

        assertThat(sugerencia).isEqualTo(franja(11, 30, 13, 30));
    }

    @Test
    void redondeaHaciaArribaAlProximoBloque() {
        assertThat(MotorDisponibilidad.redondearArriba(h(10, 0))).isEqualTo(h(10, 0));
        assertThat(MotorDisponibilidad.redondearArriba(h(10, 0).plusSeconds(5))).isEqualTo(h(10, 30));
        assertThat(MotorDisponibilidad.redondearArriba(h(10, 31))).isEqualTo(h(11, 0));
    }
}
