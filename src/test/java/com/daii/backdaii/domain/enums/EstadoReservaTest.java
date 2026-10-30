package com.daii.backdaii.domain.enums;

import com.daii.backdaii.domain.entity.Reserva;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The whole transition table of a booking: every origin against every destination. */
class EstadoReservaTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDIENTE,  PENDIENTE,  false",
            "PENDIENTE,  CONFIRMADA, true",
            "PENDIENTE,  CANCELADA,  true",
            "PENDIENTE,  FINALIZADA, false",
            "CONFIRMADA, PENDIENTE,  false",
            "CONFIRMADA, CONFIRMADA, false",
            "CONFIRMADA, CANCELADA,  true",
            "CONFIRMADA, FINALIZADA, false",
            "CANCELADA,  PENDIENTE,  false",
            "CANCELADA,  CONFIRMADA, false",
            "CANCELADA,  CANCELADA,  false",
            "CANCELADA,  FINALIZADA, false",
            "FINALIZADA, PENDIENTE,  false",
            "FINALIZADA, CONFIRMADA, false",
            "FINALIZADA, CANCELADA,  false",
            "FINALIZADA, FINALIZADA, false"
    })
    void cadaTransicionEsValidaSoloSiLaTablaLaPermite(EstadoReserva origen, EstadoReserva destino, boolean valida) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(valida);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDIENTE,  CONFIRMADA",
            "PENDIENTE,  CANCELADA",
            "CONFIRMADA, CANCELADA"
    })
    void unaTransicionValidaCambiaElEstado(EstadoReserva origen, EstadoReserva destino) {
        Reserva reserva = Reserva.builder().estado(origen).build();

        reserva.cambiarEstado(destino);

        assertThat(reserva.getEstado()).isEqualTo(destino);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDIENTE,  PENDIENTE",
            "PENDIENTE,  FINALIZADA",
            "CONFIRMADA, PENDIENTE",
            "CONFIRMADA, CONFIRMADA",
            "CONFIRMADA, FINALIZADA",
            "CANCELADA,  PENDIENTE",
            "CANCELADA,  CONFIRMADA",
            "CANCELADA,  CANCELADA",
            "CANCELADA,  FINALIZADA",
            "FINALIZADA, PENDIENTE",
            "FINALIZADA, CONFIRMADA",
            "FINALIZADA, CANCELADA",
            "FINALIZADA, FINALIZADA"
    })
    void unaTransicionInvalidaSeRechazaYNoCambiaNada(EstadoReserva origen, EstadoReserva destino) {
        Reserva reserva = Reserva.builder().estado(origen).build();

        assertThatThrownBy(() -> reserva.cambiarEstado(destino))
                .isInstanceOfSatisfying(TransicionInvalidaException.class, e -> {
                    assertThat(e.getOrigen()).isEqualTo(origen);
                    assertThat(e.getDestino()).isEqualTo(destino);
                    assertThat(e.getMessage()).isEqualTo("Una reserva " + origen.etiqueta() + " no puede pasar a " + destino.etiqueta());
                });
        assertThat(reserva.getEstado()).isEqualTo(origen);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EstadoReserva.class)
    void soloPendienteYConfirmadaOcupanLaFranja(EstadoReserva estado) {
        boolean esperado = estado == EstadoReserva.PENDIENTE || estado == EstadoReserva.CONFIRMADA;

        assertThat(estado.ocupa()).isEqualTo(esperado);
        assertThat(EstadoReserva.ACTIVOS.contains(estado)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"PENDIENTE, pendiente", "CONFIRMADA, confirmada", "CANCELADA, cancelada", "FINALIZADA, finalizada"})
    void laEtiquetaEsElNombreLegible(EstadoReserva estado, String etiqueta) {
        assertThat(estado.etiqueta()).isEqualTo(etiqueta);
    }
}
