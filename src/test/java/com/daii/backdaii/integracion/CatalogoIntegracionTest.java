package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browsing the catalog: companies, buildings, floor plans, spaces and their calendars. */
class CatalogoIntegracionTest extends IntegracionBase {

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

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
    }

    @Test
    void lasEmpresasListanSusEdificios() throws Exception {
        JsonNode empresas = leer(get("/api/empresas"));
        assertThat(empresas.size()).isEqualTo(jdbc.queryForObject("select count(*) from empresas", Integer.class));

        long empresaId = empresas.get(0).get("id").asLong();
        JsonNode detalle = leer(get("/api/empresas/{id}", empresaId));
        assertThat(detalle.get("empresa").get("id").asLong()).isEqualTo(empresaId);
        assertThat(detalle.get("edificios").size()).isEqualTo(
                jdbc.queryForObject("select count(*) from edificios where empresa_id = ?", Integer.class, empresaId));
    }

    @Test
    void losEdificiosListanSusPisos() throws Exception {
        JsonNode edificios = leer(get("/api/edificios"));
        assertThat(edificios.size()).isEqualTo(jdbc.queryForObject("select count(*) from edificios", Integer.class));

        long edificioId = edificios.get(0).get("id").asLong();
        JsonNode detalle = leer(get("/api/edificios/{id}", edificioId));
        assertThat(detalle.get("edificio").get("id").asLong()).isEqualTo(edificioId);
        assertThat(detalle.get("pisos").size()).isEqualTo(
                jdbc.queryForObject("select count(*) from pisos where edificio_id = ?", Integer.class, edificioId));
    }

    @Test
    void elPlanoDeCadaPisoMuestraTodosSusEspacios() throws Exception {
        List<Long> pisos = jdbc.queryForList("select id from pisos order by id", Long.class);
        for (Long pisoId : pisos) {
            JsonNode plano = leer(get("/api/pisos/{id}/plano", pisoId));
            int espaciosDelPiso = jdbc.queryForObject("select count(*) from espacios where piso_id = ?", Integer.class, pisoId);
            assertThat(plano.get("piso").get("id").asLong()).isEqualTo(pisoId);
            assertThat(plano.get("espacios").size()).isEqualTo(espaciosDelPiso);
            JsonNode resumen = plano.get("resumen");
            assertThat(resumen.get("total").asInt()).isEqualTo(espaciosDelPiso);
            assertThat(resumen.get("libres").asInt() + resumen.get("ocupados").asInt() + resumen.get("bloqueados").asInt())
                    .isEqualTo(espaciosDelPiso);
        }
    }

    @Test
    void elPlanoMarcaOcupadoElEspacioReservadoEnLaFranjaPedida() throws Exception {
        Espacio espacio = escenario.espacio(Escenario.ESPACIO_API);
        LocalDateTime inicio = escenario.franjaReservable();
        escenario.reservaEn(cliente, espacio, inicio, EstadoReserva.CONFIRMADA);
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where id = ?", Long.class, espacio.getId());

        JsonNode plano = leer(get("/api/pisos/{id}/plano", pisoId)
                .param("fecha", inicio.toLocalDate().toString())
                .param("desde", inicio.toLocalTime().toString())
                .param("hasta", inicio.toLocalTime().plusHours(1).toString()));

        JsonNode enPlano = buscar(plano.get("espacios"), espacio.getId());
        assertThat(enPlano.get("estado").asText()).isEqualTo("OCUPADO");
        assertThat(plano.get("resumen").get("ocupados").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void elPlanoAceptaFiltrosPorTipoCapacidadYVentana() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where tipo = 'SALA_REUNIONES' order by id limit 1", Long.class);

        JsonNode plano = leer(get("/api/pisos/{id}/plano", pisoId)
                .param("fecha", inicio.toLocalDate().toString())
                .param("desde", "10:00").param("hasta", "12:00")
                .param("tipo", "SALA_REUNIONES").param("capacidadMinima", "4").param("ventana", "true"));

        assertThat(plano.get("piso").get("id").asLong()).isEqualTo(pisoId);
        assertThat(plano.get("franja").get("inicio").asText()).isEqualTo(inicio.toLocalDate() + "T10:00:00");
    }

    @Test
    void unPlanoConElHorarioInvertidoEsUnPedidoInvalido() throws Exception {
        Long pisoId = jdbc.queryForObject("select id from pisos order by id limit 1", Long.class);
        LocalDateTime inicio = escenario.franjaReservable();

        mvc.perform(get("/api/pisos/{id}/plano", pisoId).with(como(cliente.getEmail(), Rol.CLIENTE))
                        .param("fecha", inicio.toLocalDate().toString()).param("desde", "12:00").param("hasta", "10:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losRecursosQueNoExistenResponden404() throws Exception {
        for (String ruta : List.of("/api/empresas/{id}", "/api/edificios/{id}", "/api/pisos/{id}/plano",
                "/api/pisos/{id}/espacios", "/api/espacios/{id}")) {
            mvc.perform(get(ruta, 999_999L).with(como(cliente.getEmail(), Rol.CLIENTE)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void elDetalleDelEspacioTraeSuUbicacionYPrecio() throws Exception {
        Espacio espacio = escenario.espacio(Escenario.ESPACIO_API);
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where id = ?", Long.class, espacio.getId());

        JsonNode delPiso = leer(get("/api/pisos/{id}/espacios", pisoId));
        assertThat(buscar(delPiso, espacio.getId()).get("codigo").asText()).isEqualTo(espacio.getCodigo());

        JsonNode detalle = leer(get("/api/espacios/{id}", espacio.getId()));
        assertThat(detalle.get("codigo").asText()).isEqualTo(espacio.getCodigo());
        assertThat(detalle.get("piso").get("id").asLong()).isEqualTo(pisoId);
        assertThat(detalle.get("precioHora").decimalValue()).isPositive();
    }

    @Test
    void laAgendaMuestraElDetalleSoloAlDuenoYAlAdmin() throws Exception {
        Espacio espacio = escenario.espacio(Escenario.ESPACIO_API);
        LocalDateTime inicio = escenario.franjaReservable();
        Reserva reserva = escenario.reservaEn(cliente, espacio, inicio, EstadoReserva.PENDIENTE);
        String dia = inicio.toLocalDate().toString();

        JsonNode propia = buscarReserva(leer(mvc.perform(get("/api/espacios/{id}/agenda", espacio.getId())
                .param("desde", dia).param("hasta", dia).with(como(cliente.getEmail(), Rol.CLIENTE)))), inicio);
        assertThat(propia.get("codigo").asText()).isEqualTo(reserva.getCodigo());
        assertThat(propia.get("cliente").isNull()).isTrue();

        JsonNode ajena = buscarReserva(leer(mvc.perform(get("/api/espacios/{id}/agenda", espacio.getId())
                .param("desde", dia).param("hasta", dia).with(como("otro-" + UUID.randomUUID() + "@x.local", Rol.CLIENTE)))), inicio);
        assertThat(ajena.get("codigo").isNull()).isTrue();

        JsonNode delAdmin = buscarReserva(leer(mvc.perform(get("/api/espacios/{id}/agenda", espacio.getId())
                .param("desde", dia).param("hasta", dia).with(comoAdmin()))), inicio);
        assertThat(delAdmin.get("cliente").asText()).isEqualTo(cliente.getNombre());
    }

    @Test
    void laAgendaRechazaUnRangoInvalido() throws Exception {
        Espacio espacio = escenario.espacio(Escenario.ESPACIO_API);
        LocalDateTime inicio = escenario.franjaReservable();

        mvc.perform(get("/api/espacios/{id}/agenda", espacio.getId()).with(como(cliente.getEmail(), Rol.CLIENTE))
                        .param("desde", inicio.toLocalDate().toString())
                        .param("hasta", inicio.toLocalDate().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/espacios/{id}/agenda", espacio.getId()).with(como(cliente.getEmail(), Rol.CLIENTE))
                        .param("desde", inicio.toLocalDate().toString())
                        .param("hasta", inicio.toLocalDate().plusDays(400).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lasFranjasLibresEsquivanLasReservas() throws Exception {
        Espacio espacio = escenario.espacio(Escenario.ESPACIO_API);
        LocalDateTime inicio = escenario.franjaReservable();
        escenario.reservaEn(cliente, espacio, inicio, EstadoReserva.CONFIRMADA);

        JsonNode franjas = leer(mvc.perform(get("/api/espacios/{id}/franjas-libres", espacio.getId())
                .with(como(cliente.getEmail(), Rol.CLIENTE))
                .param("fecha", inicio.toLocalDate().toString())
                .param("duracion", "60")
                .param("cerca", inicio.toLocalTime().toString())));

        assertThat(franjas.size()).isPositive();
        for (JsonNode franja : franjas) {
            LocalDateTime desde = LocalDateTime.parse(franja.get("inicio").asText());
            LocalDateTime hasta = LocalDateTime.parse(franja.get("fin").asText());
            assertThat(hasta.isAfter(inicio) && desde.isBefore(inicio.plusHours(1))).isFalse();
        }
    }

    private JsonNode leer(MockHttpServletRequestBuilder pedido) throws Exception {
        return leer(mvc.perform(pedido.with(como(cliente.getEmail(), Rol.CLIENTE))));
    }

    private JsonNode leer(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private static JsonNode buscar(JsonNode lista, Long id) {
        for (JsonNode nodo : lista) {
            if (nodo.get("id").asLong() == id) {
                return nodo;
            }
        }
        throw new AssertionError("No está el id " + id + " en " + lista);
    }

    private static JsonNode buscarReserva(JsonNode agenda, LocalDateTime inicio) {
        for (JsonNode ocupacion : agenda.get("ocupaciones")) {
            if ("RESERVA".equals(ocupacion.get("tipo").asText())
                    && LocalDateTime.parse(ocupacion.get("inicio").asText()).equals(inicio)) {
                return ocupacion;
            }
        }
        throw new AssertionError("No está la reserva de las " + inicio + " en " + agenda);
    }
}
