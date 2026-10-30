package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The repository's own queries and the database constraints, on a real PostgreSQL. Each test rolls back. */
@Transactional
class ReservaRepositoryIntegracionTest extends IntegracionBase {

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private MedioPagoRepository medios;
    @Autowired
    private CobroRepository cobros;
    @Autowired
    private Clock reloj;

    private Escenario escenario;
    private Usuario cliente;
    private LocalDateTime ahora;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
        ahora = LocalDateTime.now(reloj);
    }

    @Test
    void cobrosSinResolverTraeSoloLasPendientesViejasYSinCobro() {
        Reserva vieja = conSolicitud(escenario.futura(cliente, EstadoReserva.PENDIENTE), ahora.minusMinutes(10));
        Reserva reciente = conSolicitud(escenario.futura(cliente, EstadoReserva.PENDIENTE), ahora);
        Reserva confirmada = conSolicitud(escenario.futura(cliente, EstadoReserva.CONFIRMADA), ahora.minusMinutes(10));
        Reserva yaCobrada = conSolicitud(escenario.futura(cliente, EstadoReserva.PENDIENTE), ahora.minusMinutes(10));
        cobros.save(Cobro.builder().reserva(yaCobrada).monto(BigDecimal.TEN).estado(EstadoCobro.APROBADO)
                .procesadoEl(ahora).build());
        Reserva nuncaEncolada = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        List<Long> ids = reservas.findCobrosSinResolver(EstadoReserva.PENDIENTE, ahora.minusMinutes(2),
                PageRequest.of(0, 500)).stream().map(Reserva::getId).toList();

        assertThat(ids).contains(vieja.getId())
                .doesNotContain(reciente.getId(), confirmada.getId(), yaCobrada.getId(), nuncaEncolada.getId());
    }

    @Test
    void lasReservasDelClienteSeSuperponenConRangosSemiabiertos() {
        Reserva reserva = escenario.futura(cliente, EstadoReserva.CONFIRMADA);
        Reserva cancelada = escenario.futura(cliente, EstadoReserva.CANCELADA);

        assertThat(reservas.findDelUsuarioEnRango(cliente.getId(), reserva.getInicio().plusMinutes(30),
                reserva.getFin().plusHours(1), EstadoReserva.ACTIVOS)).extracting(Reserva::getId).containsExactly(reserva.getId());
        // Ends at 11:00 and the range starts at 11:00: they touch, they do not overlap.
        assertThat(reservas.findDelUsuarioEnRango(cliente.getId(), reserva.getFin(), reserva.getFin().plusHours(1),
                EstadoReserva.ACTIVOS)).isEmpty();
        assertThat(reservas.findDelUsuarioEnRango(cliente.getId(), cancelada.getInicio(), cancelada.getFin(),
                EstadoReserva.ACTIVOS)).isEmpty();
    }

    @Test
    void laBaseNoAdmiteDosReservasActivasSuperpuestasDelMismoEspacio() {
        Reserva reserva = escenario.futura(cliente, EstadoReserva.CONFIRMADA);
        Reserva encima = Reserva.builder().usuario(cliente).creadaPor(cliente).espacio(reserva.getEspacio())
                .inicio(reserva.getInicio().plusMinutes(30)).fin(reserva.getFin().plusMinutes(30))
                .estado(EstadoReserva.PENDIENTE).build();

        assertThatThrownBy(() -> reservas.saveAndFlush(encima)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaReservaCanceladaNoOcupaLaFranjaEnLaBase() {
        Reserva cancelada = escenario.futura(cliente, EstadoReserva.CANCELADA);
        Reserva nueva = Reserva.builder().usuario(cliente).creadaPor(cliente).espacio(cancelada.getEspacio())
                .inicio(cancelada.getInicio()).fin(cancelada.getFin()).estado(EstadoReserva.PENDIENTE).build();

        assertThat(reservas.saveAndFlush(nueva).getId()).isNotNull();
    }

    @Test
    void laBaseNoAdmiteDosCobrosParaLaMismaReserva() {
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        cobros.saveAndFlush(Cobro.builder().reserva(reserva).monto(BigDecimal.TEN).estado(EstadoCobro.APROBADO)
                .procesadoEl(ahora).build());

        assertThatThrownBy(() -> cobros.saveAndFlush(Cobro.builder().reserva(reserva).monto(BigDecimal.TEN)
                .estado(EstadoCobro.APROBADO).procesadoEl(ahora).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Reserva conSolicitud(Reserva reserva, LocalDateTime cuando) {
        reserva.setCobroSolicitadoEl(cuando);
        return reservas.save(reserva);
    }
}
