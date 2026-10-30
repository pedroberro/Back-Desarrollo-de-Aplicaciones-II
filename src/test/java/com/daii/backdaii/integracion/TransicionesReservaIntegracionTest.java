package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AdminReservaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.util.stream.Stream;

import static com.daii.backdaii.domain.enums.EstadoReserva.CANCELADA;
import static com.daii.backdaii.domain.enums.EstadoReserva.CONFIRMADA;
import static com.daii.backdaii.domain.enums.EstadoReserva.FINALIZADA;
import static com.daii.backdaii.domain.enums.EstadoReserva.PENDIENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * State changes of a booking through the API, down to the database: every invalid transition
 * answers 409 and leaves the stored state as it was.
 */
class TransicionesReservaIntegracionTest extends IntegracionBase {

    private static final String MOTIVO = "Prueba de transicion de estado";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private MedioPagoRepository medios;
    @Autowired
    private Clock reloj;

    private Escenario escenario;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
    }

    /** FINALIZADA as origin is an active booking whose end already passed. */
    static Stream<Arguments> invalidas() {
        return Stream.of(
                arguments(CONFIRMADA, PENDIENTE),
                arguments(CANCELADA, PENDIENTE),
                arguments(CANCELADA, CONFIRMADA),
                arguments(PENDIENTE, FINALIZADA),
                arguments(CONFIRMADA, FINALIZADA),
                arguments(CANCELADA, FINALIZADA),
                arguments(FINALIZADA, PENDIENTE),
                arguments(FINALIZADA, CONFIRMADA),
                arguments(FINALIZADA, CANCELADA));
    }

    static Stream<Arguments> validas() {
        return Stream.of(
                arguments(PENDIENTE, CONFIRMADA),
                arguments(PENDIENTE, CANCELADA),
                arguments(CONFIRMADA, CANCELADA));
    }

    @ParameterizedTest(name = "{0} -> {1} responde 409")
    @MethodSource("invalidas")
    void elAdminNoPuedeHacerUnaTransicionInvalida(EstadoReserva origen, EstadoReserva destino) throws Exception {
        Reserva reserva = origen == FINALIZADA ? escenario.terminada(cliente) : escenario.futura(cliente, origen);
        EstadoReserva guardado = reserva.getEstado();

        editarComoAdmin(reserva, destino)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSICION_INVALIDA"));

        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(guardado);
    }

    @ParameterizedTest(name = "{0} -> {1} responde 200")
    @MethodSource("validas")
    void elAdminHaceLasTransicionesValidas(EstadoReserva origen, EstadoReserva destino) throws Exception {
        Reserva reserva = escenario.futura(cliente, origen);

        editarComoAdmin(reserva, destino)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(destino.name()));

        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(destino);
    }

    @Test
    void elClienteNoPuedeCancelarUnaReservaYaCancelada() throws Exception {
        Reserva reserva = escenario.futura(cliente, CANCELADA);

        cancelarComoCliente(reserva)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_NO_CANCELABLE"));

        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(CANCELADA);
    }

    @Test
    void elClienteNoPuedeCancelarUnaReservaTerminada() throws Exception {
        Reserva reserva = escenario.terminada(cliente);

        cancelarComoCliente(reserva)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_NO_CANCELABLE"));

        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(CONFIRMADA);
    }

    @Test
    void elClienteCancelaUnaReservaConfirmadaQueTodaviaNoEmpezo() throws Exception {
        Reserva reserva = escenario.futura(cliente, CONFIRMADA);

        cancelarComoCliente(reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(CANCELADA);
    }

    private ResultActions editarComoAdmin(Reserva reserva, EstadoReserva destino) throws Exception {
        AdminReservaRequest pedido = new AdminReservaRequest(cliente.getId(), reserva.getEspacio().getId(),
                reserva.getInicio(), reserva.getFin(), destino, MOTIVO);
        return mvc.perform(put("/api/admin/reservas/{id}", reserva.getId())
                .with(user(Escenario.ADMIN).authorities(new SimpleGrantedAuthority("RESERVAS_GESTIONAR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(pedido)));
    }

    private ResultActions cancelarComoCliente(Reserva reserva) throws Exception {
        return mvc.perform(patch("/api/reservas/{id}/cancelar", reserva.getId())
                .with(user(cliente.getEmail()).authorities(new SimpleGrantedAuthority("RESERVAS_CREAR"))));
    }
}
