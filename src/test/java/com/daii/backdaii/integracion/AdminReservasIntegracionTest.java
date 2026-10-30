package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The administrator's booking screens: list with filters, floor agenda, create, edit and delete. */
class AdminReservasIntegracionTest extends IntegracionBase {

    private static final String MOTIVO = "Pedido del cliente por telefono";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
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
    private Espacio espacio;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
        espacio = escenario.espacio(Escenario.ESPACIO_API);
    }

    @Test
    void elAdminReservaANombreDeUnCliente() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();

        long id = json.readTree(guardar(post("/api/admin/reservas"), cliente.getId(), espacio.getId(), inicio, null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.creadaPorAdmin").value(true))
                .andExpect(jsonPath("$.cliente.id").value(cliente.getId()))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        Reserva guardada = reservas.findDetalleById(id).orElseThrow();
        assertThat(guardada.getCreadaPor().getEmail()).isEqualTo(Escenario.ADMIN);
        assertThat(guardada.getUsuario().getId()).isEqualTo(cliente.getId());
    }

    @Test
    void elAdminNoReservaANombreDeOtroAdmin() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();

        guardar(post("/api/admin/reservas"), escenario.admin().getId(), espacio.getId(), inicio, null, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAdminMueveUnaReservaDeHorarioYDeEspacioYQuedaEnLaBitacora() throws Exception {
        Reserva reserva = escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.CONFIRMADA);
        Espacio otro = escenario.espacio(Escenario.ESPACIO_BLOQUEOS);
        LocalDateTime nuevoInicio = escenario.franjaReservable();

        guardar(put("/api/admin/reservas/" + reserva.getId()), cliente.getId(), otro.getId(), nuevoInicio,
                EstadoReserva.CONFIRMADA, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.espacio.id").value(otro.getId()))
                .andExpect(jsonPath("$.cambios[0].motivo").value(MOTIVO));

        Reserva movida = reservas.findById(reserva.getId()).orElseThrow();
        assertThat(movida.getInicio()).isEqualTo(nuevoInicio);
        assertThat(movida.getEspacio().getId()).isEqualTo(otro.getId());
    }

    @Test
    void elAdminPasaUnaReservaAOtroCliente() throws Exception {
        Reserva reserva = escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.PENDIENTE);
        Usuario otroCliente = escenario.cliente();

        guardar(put("/api/admin/reservas/" + reserva.getId()), otroCliente.getId(), espacio.getId(), reserva.getInicio(),
                null, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.id").value(otroCliente.getId()));
    }

    @Test
    void editarSinCambiosOSinMotivoEsUnPedidoInvalido() throws Exception {
        Reserva reserva = escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.PENDIENTE);

        guardar(put("/api/admin/reservas/" + reserva.getId()), cliente.getId(), espacio.getId(), reserva.getInicio(),
                EstadoReserva.PENDIENTE, MOTIVO)
                .andExpect(status().isBadRequest());
        guardar(put("/api/admin/reservas/" + reserva.getId()), cliente.getId(), espacio.getId(), reserva.getInicio(),
                EstadoReserva.CONFIRMADA, "corto")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIVO_REQUERIDO"));
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.PENDIENTE);
    }

    @Test
    void moverAUnaFranjaTomadaRespondeConflicto() throws Exception {
        Reserva reserva = escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.CONFIRMADA);
        LocalDateTime tomada = escenario.franjaReservable();
        escenario.reservaEn(escenario.cliente(), espacio, tomada, EstadoReserva.CONFIRMADA);

        guardar(put("/api/admin/reservas/" + reserva.getId()), cliente.getId(), espacio.getId(), tomada,
                EstadoReserva.CONFIRMADA, MOTIVO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRANJA_OCUPADA"));
    }

    @Test
    void eliminarUnaReservaFuturaLaCancelaYLaBorra() throws Exception {
        Reserva futura = escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.CONFIRMADA);
        Reserva pasada = escenario.terminada(cliente);

        mvc.perform(delete("/api/admin/reservas/{id}", futura.getId()).with(comoAdmin())).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/reservas/{id}", pasada.getId()).with(comoAdmin())).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/reservas/{id}", 999_999L).with(comoAdmin())).andExpect(status().isNotFound());

        assertThat(reservas.findById(futura.getId())).isEmpty();
        assertThat(reservas.findById(pasada.getId())).isEmpty();
    }

    @Test
    void elListadoFiltraPorClienteEstadoYUbicacion() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        Reserva reserva = escenario.reservaEn(cliente, espacio, inicio, EstadoReserva.CONFIRMADA);
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where id = ?", Long.class, espacio.getId());
        Long edificioId = jdbc.queryForObject("select edificio_id from pisos where id = ?", Long.class, pisoId);
        String dia = inicio.toLocalDate().toString();

        JsonNode pagina = listar(Map.of("q", cliente.getEmail(), "estado", "CONFIRMADA", "espacioId", espacio.getId().toString(),
                "pisoId", pisoId.toString(), "edificioId", edificioId.toString(), "tipo", espacio.getTipo().name(),
                "desde", dia, "hasta", dia));
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(1);
        assertThat(pagina.get("content").get(0).get("id").asLong()).isEqualTo(reserva.getId());
        assertThat(pagina.get("totalGeneral").asLong()).isGreaterThanOrEqualTo(1);

        assertThat(listar(Map.of("q", reserva.getCodigo())).get("content").get(0).get("id").asLong()).isEqualTo(reserva.getId());
        assertThat(listar(Map.of("q", cliente.getEmail(), "estado", "CANCELADA")).get("totalElements").asLong()).isZero();
    }

    @Test
    void elListadoDistingueFinalizadasDeActivas() throws Exception {
        escenario.terminada(cliente);
        escenario.reservaEn(cliente, espacio, escenario.franjaReservable(), EstadoReserva.PENDIENTE);

        assertThat(listar(Map.of("q", cliente.getEmail(), "estado", "FINALIZADA")).get("totalElements").asLong()).isEqualTo(1);
        assertThat(listar(Map.of("q", cliente.getEmail(), "estado", "PENDIENTE")).get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void laAgendaDelPisoTraeLasReservasDelDia() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        Reserva reserva = escenario.reservaEn(cliente, espacio, inicio, EstadoReserva.PENDIENTE);
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where id = ?", Long.class, espacio.getId());

        JsonNode agenda = json.readTree(mvc.perform(get("/api/admin/agenda").with(comoAdmin())
                        .param("pisoId", pisoId.toString()).param("fecha", inicio.toLocalDate().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(agenda.get("piso").get("id").asLong()).isEqualTo(pisoId);
        assertThat(agenda.get("bloques").findValues("id")).extracting(JsonNode::asLong).contains(reserva.getId());
    }

    @Test
    void laBusquedaDeClientesEncuentraPorEmail() throws Exception {
        mvc.perform(get("/api/admin/clientes").with(comoAdmin()).param("q", cliente.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cliente.getId()));
    }

    @Test
    void unClienteNoEntraAlPanelDelAdmin() throws Exception {
        mvc.perform(get("/api/admin/reservas").with(como(cliente.getEmail(), com.daii.backdaii.domain.enums.Rol.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    private JsonNode listar(Map<String, String> filtros) throws Exception {
        var pedido = get("/api/admin/reservas").with(comoAdmin());
        filtros.forEach(pedido::param);
        return json.readTree(mvc.perform(pedido).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions guardar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido,
                                  Long usuarioId, Long espacioId, LocalDateTime inicio, EstadoReserva estado,
                                  String motivo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("usuarioId", usuarioId);
        cuerpo.put("espacioId", espacioId);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plusHours(1).toString());
        cuerpo.put("estado", estado);
        cuerpo.put("motivo", motivo);
        return mvc.perform(pedido.with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }
}
