package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.domain.evento.CobroReencolado;
import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.domain.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarridoCobrosTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ApplicationEventPublisher eventos;

    private BarridoCobros barrido;

    @BeforeEach
    void crear() {
        Clock reloj = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        barrido = new BarridoCobros(reservaRepository, eventos, reloj, 120, 3);
    }

    private Reserva pendiente(int reencolados) {
        Usuario ana = Usuario.builder().id(3L).nombre("Ana").email("ana@example.com").build();
        Espacio espacio = Espacio.builder().id(11L).codigo("C-01").tipo(TipoEspacio.CUBICULO).build();
        Reserva reserva = Reserva.builder().id(7L).espacio(espacio).usuario(ana).creadaPor(ana)
                .inicio(AHORA.plusDays(1)).fin(AHORA.plusDays(1).plusHours(1)).estado(EstadoReserva.PENDIENTE)
                .cobroSolicitadoEl(AHORA.minusMinutes(5)).reencoladosCobro(reencolados).build();
        when(reservaRepository.findCobrosSinResolver(eq(EstadoReserva.PENDIENTE), eq(AHORA.minusSeconds(120)), any()))
                .thenReturn(List.of(reserva));
        when(reservaRepository.findBloqueadaById(7L)).thenReturn(Optional.of(reserva));
        return reserva;
    }

    @Test
    void reencolaUnCobroSinRespuestaYCuentaElIntento() {
        Reserva reserva = pendiente(0);

        BarridoCobros.Resultado resultado = barrido.barrer();

        assertThat(resultado).isEqualTo(new BarridoCobros.Resultado(1, 0));
        assertThat(reserva.getReencoladosCobro()).isEqualTo(1);
        assertThat(reserva.getCobroSolicitadoEl()).isEqualTo(AHORA);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        verify(eventos).publishEvent(new CobroReencolado(7L, 1));
    }

    @Test
    void agotadosLosIntentosCancelaLaReservaYLiberaLaFranja() {
        Reserva reserva = pendiente(3);

        BarridoCobros.Resultado resultado = barrido.barrer();

        assertThat(resultado).isEqualTo(new BarridoCobros.Resultado(0, 1));
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        ArgumentCaptor<CobroResuelto> aviso = ArgumentCaptor.forClass(CobroResuelto.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().aprobado()).isFalse();
        assertThat(aviso.getValue().motivoRechazo()).isEqualTo(BarridoCobros.MOTIVO_ABANDONO);
    }

    @Test
    void siMientrasTantoSeResolvioNoHaceNada() {
        Reserva reserva = pendiente(0);
        reserva.cambiarEstado(EstadoReserva.CONFIRMADA);

        assertThat(barrido.barrer()).isEqualTo(new BarridoCobros.Resultado(0, 0));
        verifyNoInteractions(eventos);
        assertThat(reserva.getReencoladosCobro()).isZero();
    }

    @Test
    void registrarLaSolicitudMarcaLaHora() {
        Reserva reserva = Reserva.builder().id(9L).estado(EstadoReserva.PENDIENTE).build();
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));

        barrido.registrarSolicitud(9L);

        assertThat(reserva.getCobroSolicitadoEl()).isEqualTo(AHORA);
    }
}
