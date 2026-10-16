package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.MensajeProcesadoRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CobroServiceImplTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private MedioPagoRepository medioPagoRepository;
    @Mock
    private CobroRepository cobroRepository;
    @Mock
    private MensajeProcesadoRepository mensajesProcesados;
    @Mock
    private PasarelaPagos pasarela;
    @Mock
    private ApplicationEventPublisher eventos;

    private CobroServiceImpl servicio;
    private Usuario ana;
    private MedioPago visa;

    @BeforeEach
    void crear() {
        Tarifario tarifario = new Tarifario(Map.of(
                TipoEspacio.CUBICULO, new BigDecimal("3500"),
                TipoEspacio.SALA_REUNIONES, new BigDecimal("9000"),
                TipoEspacio.SALA_PRESENTACIONES, new BigDecimal("14000"),
                TipoEspacio.SALA_CONFERENCIAS, new BigDecimal("22000")));
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        servicio = new CobroServiceImpl(reservaRepository, medioPagoRepository, cobroRepository, mensajesProcesados, pasarela, tarifario, eventos, reloj);

        ana = Usuario.builder().id(3L).nombre("Ana").email("ana@example.com").build();
        visa = MedioPago.builder().id(5L).usuario(ana).numeroTarjetaMasked("**** **** **** 4242")
                .titular("Ana Pérez").eliminado(false).build();
    }

    private Reserva reserva(EstadoReserva estado, MedioPago medio) {
        Espacio espacio = Espacio.builder().id(11L).codigo("C-101").tipo(TipoEspacio.CUBICULO).build();
        Reserva reserva = Reserva.builder().id(7L).espacio(espacio).usuario(ana).creadaPor(ana)
                .inicio(INICIO).fin(INICIO.plusHours(2)).estado(estado).medioPago(medio).build();
        when(reservaRepository.findBloqueadaById(7L)).thenReturn(Optional.of(reserva));
        return reserva;
    }

    private Cobro cobroGuardado() {
        ArgumentCaptor<Cobro> cobro = ArgumentCaptor.forClass(Cobro.class);
        verify(cobroRepository).save(cobro.capture());
        return cobro.getValue();
    }

    private CobroResuelto eventoPublicado() {
        ArgumentCaptor<CobroResuelto> evento = ArgumentCaptor.forClass(CobroResuelto.class);
        verify(eventos).publishEvent(evento.capture());
        return evento.getValue();
    }

    @Test
    void conElPagoAprobadoConfirmaLaReservaYGuardaElCobro() {
        Reserva reserva = reserva(EstadoReserva.PENDIENTE, visa);
        when(pasarela.cobrar(any())).thenReturn(ResultadoPago.aprobado("SIM-1"));

        servicio.procesar(7L, null);

        ArgumentCaptor<SolicitudPago> solicitud = ArgumentCaptor.forClass(SolicitudPago.class);
        verify(pasarela).cobrar(solicitud.capture());
        assertThat(solicitud.getValue().monto()).isEqualByComparingTo("7000");
        assertThat(solicitud.getValue().ultimos4()).isEqualTo("4242");

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        Cobro cobro = cobroGuardado();
        assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.APROBADO);
        assertThat(cobro.getReferencia()).isEqualTo("SIM-1");
        assertThat(cobro.getMedioPago()).isSameAs(visa);
        assertThat(eventoPublicado().aprobado()).isTrue();
    }

    @Test
    void conElPagoRechazadoCancelaLaReservaParaLiberarLaFranja() {
        Reserva reserva = reserva(EstadoReserva.PENDIENTE, visa);
        when(pasarela.cobrar(any())).thenReturn(ResultadoPago.rechazado("Fondos insuficientes"));

        servicio.procesar(7L, null);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        Cobro cobro = cobroGuardado();
        assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.RECHAZADO);
        assertThat(cobro.getMotivoRechazo()).isEqualTo("Fondos insuficientes");
        CobroResuelto evento = eventoPublicado();
        assertThat(evento.aprobado()).isFalse();
        assertThat(evento.motivoRechazo()).isEqualTo("Fondos insuficientes");
    }

    @Test
    void sinTarjetaElegidaCobraLaPrincipalDelCliente() {
        reserva(EstadoReserva.PENDIENTE, null);
        when(medioPagoRepository.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(3L)).thenReturn(List.of(visa));
        when(pasarela.cobrar(any())).thenReturn(ResultadoPago.aprobado("SIM-2"));

        servicio.procesar(7L, null);

        assertThat(cobroGuardado().getMedioPago()).isSameAs(visa);
    }

    @Test
    void sinNingunaTarjetaRechazaSinLlamarALaPasarela() {
        Reserva reserva = reserva(EstadoReserva.PENDIENTE, null);
        when(medioPagoRepository.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(3L)).thenReturn(List.of());

        servicio.procesar(7L, null);

        verifyNoInteractions(pasarela);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cobroGuardado().getMedioPago()).isNull();
    }

    @Test
    void unaReservaQueYaNoEstaPendienteNoSeCobra() {
        Reserva reserva = reserva(EstadoReserva.CANCELADA, visa);

        servicio.procesar(7L, null);

        verifyNoInteractions(pasarela, eventos);
        verify(cobroRepository, never()).save(any());
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void unMensajeRepetidoNoCobraDosVeces() {
        reserva(EstadoReserva.PENDIENTE, visa);
        when(cobroRepository.existsByReservaId(7L)).thenReturn(true);

        servicio.procesar(7L, null);

        verifyNoInteractions(pasarela, eventos);
        verify(cobroRepository, never()).save(any());
    }

    @Test
    void unaFallaTecnicaDeLaPasarelaSePropagaSinEscribirNada() {
        Reserva reserva = reserva(EstadoReserva.PENDIENTE, visa);
        when(pasarela.cobrar(any())).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> servicio.procesar(7L, null)).isInstanceOf(IllegalStateException.class);

        verify(cobroRepository, never()).save(any());
        verifyNoInteractions(eventos);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
    }

    @Test
    void unMensajeYaProcesadoNoSeVuelveACobrar() {
        when(mensajesProcesados.existsById("m-1")).thenReturn(true);

        servicio.procesar(7L, "m-1");

        verifyNoInteractions(pasarela, reservaRepository, cobroRepository, eventos);
    }

    @Test
    void elMensajeQuedaRegistradoComoProcesado() {
        reserva(EstadoReserva.PENDIENTE, visa);
        when(pasarela.cobrar(any())).thenReturn(ResultadoPago.aprobado("SIM-9"));

        servicio.procesar(7L, "m-2");

        verify(mensajesProcesados).save(org.mockito.ArgumentMatchers.argThat(m -> m.getMensajeId().equals("m-2")));
    }
}
