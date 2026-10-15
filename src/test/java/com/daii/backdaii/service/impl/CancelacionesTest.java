package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaCancelada.Motivo;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelacionesTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Mock
    private CobroRepository cobroRepository;
    @Mock
    private PasarelaPagos pasarela;
    @Mock
    private ApplicationEventPublisher eventos;

    private Cancelaciones cancelaciones;

    @BeforeEach
    void crear() {
        Clock reloj = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        cancelaciones = new Cancelaciones(cobroRepository, pasarela, eventos, reloj);
    }

    private static Reserva reserva(LocalDateTime inicio) {
        Usuario ana = Usuario.builder().id(3L).nombre("Ana").email("ana@example.com").build();
        Espacio espacio = Espacio.builder().id(11L).codigo("C-01").tipo(TipoEspacio.CUBICULO).build();
        return Reserva.builder().id(7L).espacio(espacio).usuario(ana).creadaPor(ana)
                .inicio(inicio).fin(inicio.plusHours(2)).estado(EstadoReserva.CONFIRMADA).build();
    }

    private Cobro cobrada(String monto) {
        Cobro cobro = Cobro.builder().id(1L).monto(new BigDecimal(monto)).estado(EstadoCobro.APROBADO).referencia("SIM-1").build();
        when(cobroRepository.findByReservaId(7L)).thenReturn(Optional.of(cobro));
        return cobro;
    }

    private ReservaCancelada evento() {
        ArgumentCaptor<ReservaCancelada> evento = ArgumentCaptor.forClass(ReservaCancelada.class);
        verify(eventos).publishEvent(evento.capture());
        return evento.getValue();
    }

    @Test
    void elClienteConMasDeUnDiaRecuperaTodo() {
        Reserva reserva = reserva(AHORA.plusDays(2));
        Cobro cobro = cobrada("7000.00");
        when(pasarela.reintegrar(any())).thenReturn(ResultadoPago.aprobado("SIM-R-1"));

        cancelaciones.cancelar(reserva, Motivo.CLIENTE, null);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cobro.getMontoReintegrado()).isEqualByComparingTo("7000");
        assertThat(cobro.getReintegroReferencia()).isEqualTo("SIM-R-1");
        assertThat(evento().porcentaje()).isEqualTo(100);
    }

    @Test
    void elClienteElMismoDiaRecuperaLaMitad() {
        Reserva reserva = reserva(AHORA.plusHours(5));
        Cobro cobro = cobrada("7000.00");
        when(pasarela.reintegrar(any())).thenReturn(ResultadoPago.aprobado("SIM-R-2"));

        cancelaciones.cancelar(reserva, Motivo.CLIENTE, null);

        assertThat(cobro.getMontoReintegrado()).isEqualByComparingTo("3500");
        assertThat(evento().reintegrado()).isEqualByComparingTo("3500");
    }

    @Test
    void sobreLaHoraNoHayReintegroPeroQuedaSaldado() {
        Reserva reserva = reserva(AHORA.plusMinutes(90));
        Cobro cobro = cobrada("7000.00");

        cancelaciones.cancelar(reserva, Motivo.CLIENTE, null);

        verifyNoInteractions(pasarela);
        assertThat(cobro.getMontoReintegrado()).isEqualByComparingTo("0");
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void unAdministradorDevuelveTodoAunqueFalteMenosDeUnaHora() {
        Reserva reserva = reserva(AHORA.plusMinutes(30));
        Cobro cobro = cobrada("7000.00");
        when(pasarela.reintegrar(any())).thenReturn(ResultadoPago.aprobado("SIM-R-3"));

        cancelaciones.cancelar(reserva, Motivo.BLOQUEO, "mantenimiento");

        assertThat(cobro.getMontoReintegrado()).isEqualByComparingTo("7000");
        assertThat(evento().motivo()).isEqualTo(Motivo.BLOQUEO);
    }

    @Test
    void sinCobroSeCancelaSinLlamarALaPasarela() {
        Reserva reserva = reserva(AHORA.plusDays(1));
        when(cobroRepository.findByReservaId(7L)).thenReturn(Optional.empty());

        cancelaciones.cancelar(reserva, Motivo.CLIENTE, null);

        verifyNoInteractions(pasarela);
        assertThat(evento().cobrado()).isNull();
    }

    @Test
    void siLaPasarelaRechazaElReintegroLaReservaNoSeCancela() {
        Reserva reserva = reserva(AHORA.plusDays(2));
        Cobro cobro = cobrada("7000.00");
        when(pasarela.reintegrar(any())).thenReturn(ResultadoPago.rechazado("sin conexión"));

        assertThatThrownBy(() -> cancelaciones.cancelar(reserva, Motivo.CLIENTE, null)).isInstanceOf(ConflictException.class);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(cobro.getMontoReintegrado()).isNull();
        verifyNoInteractions(eventos);
    }
}
