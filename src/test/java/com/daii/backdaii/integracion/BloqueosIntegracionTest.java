package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Blocking a space or a whole floor, and what happens to the bookings in the way. */
class BloqueosIntegracionTest extends IntegracionBase {

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
    private Espacio espacio;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        espacio = escenario.espacio(Escenario.ESPACIO_BLOQUEOS);
    }

    @Test
    void bloqueaUnEspacioYApareceEntreLosVigentes() throws Exception {
        LocalDateTime dia = escenario.diaLejano();

        JsonNode creados = leer(bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9), dia.plusHours(13), null)
                .andExpect(status().isCreated()));
        assertThat(creados.get("cantidad").asInt()).isEqualTo(1);
        long id = creados.get("bloqueos").get(0).get("id").asLong();

        JsonNode vigentes = leer(mvc.perform(get("/api/admin/bloqueos").with(comoAdmin())).andExpect(status().isOk()));
        assertThat(vigentes.findValues("id")).extracting(JsonNode::asLong).contains(id);
    }

    @Test
    void noSePisaUnBloqueoConOtro() throws Exception {
        LocalDateTime dia = escenario.diaLejano();
        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9), dia.plusHours(13), null).andExpect(status().isCreated());

        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(12), dia.plusHours(15), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRANJA_OCUPADA"));
    }

    @Test
    void unBloqueoSobreReservasPideConfirmacionYDespuesLasCancela() throws Exception {
        LocalDateTime dia = escenario.diaLejano();
        Reserva reserva = escenario.reservaEn(escenario.cliente(), espacio, dia.plusHours(10), EstadoReserva.CONFIRMADA);

        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9), dia.plusHours(13), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVAS_AFECTADAS"))
                .andExpect(jsonPath("$.reservas[0].id").value(reserva.getId()));
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA);

        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9), dia.plusHours(13), true)
                .andExpect(status().isCreated());
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void bloquearUnPisoBloqueaCadaEspacioYSeBorraEnGrupo() throws Exception {
        Long pisoId = jdbc.queryForObject("select id from pisos order by id desc limit 1", Long.class);
        int espaciosDelPiso = jdbc.queryForObject("select count(*) from espacios where piso_id = ?", Integer.class, pisoId);
        LocalDateTime dia = escenario.diaLejano();

        JsonNode creados = leer(bloquear("PISO", null, pisoId, dia.plusHours(8), dia.plusHours(18), null)
                .andExpect(status().isCreated()));
        assertThat(creados.get("cantidad").asInt()).isEqualTo(espaciosDelPiso);
        long uno = creados.get("bloqueos").get(0).get("id").asLong();
        String grupo = creados.get("grupo").asText();

        mvc.perform(delete("/api/admin/bloqueos/{id}", uno).param("grupo", "true").with(comoAdmin()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from bloqueos where grupo = ?::uuid", Integer.class, grupo)).isZero();
    }

    @Test
    void borrarUnBloqueoSueltoDejaElRestoDelGrupo() throws Exception {
        Long pisoId = jdbc.queryForObject("select id from pisos order by id desc limit 1", Long.class);
        LocalDateTime dia = escenario.diaLejano();
        JsonNode creados = leer(bloquear("PISO", null, pisoId, dia.plusHours(8), dia.plusHours(18), null)
                .andExpect(status().isCreated()));
        long uno = creados.get("bloqueos").get(0).get("id").asLong();
        int cantidad = creados.get("cantidad").asInt();

        mvc.perform(delete("/api/admin/bloqueos/{id}", uno).with(comoAdmin())).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/bloqueos/{id}", uno).with(comoAdmin())).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from bloqueos where grupo = ?::uuid", Integer.class,
                creados.get("grupo").asText())).isEqualTo(cantidad - 1);
    }

    @Test
    void elPlanoMuestraBloqueadoElEspacio() throws Exception {
        LocalDateTime dia = escenario.diaLejano();
        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9), dia.plusHours(13), null).andExpect(status().isCreated());
        Long pisoId = jdbc.queryForObject("select piso_id from espacios where id = ?", Long.class, espacio.getId());

        JsonNode plano = leer(mvc.perform(get("/api/pisos/{id}/plano", pisoId).with(comoAdmin())
                .param("fecha", dia.toLocalDate().toString()).param("desde", "10:00").param("hasta", "11:00")));

        for (JsonNode enPlano : plano.get("espacios")) {
            if (enPlano.get("id").asLong() == espacio.getId()) {
                assertThat(enPlano.get("estado").asText()).isEqualTo("BLOQUEADO");
                return;
            }
        }
        throw new AssertionError("El espacio no está en el plano");
    }

    @Test
    void lasReglasDelBloqueoSeValidan() throws Exception {
        LocalDateTime dia = escenario.diaLejano();
        LocalDateTime ahora = LocalDateTime.now(reloj);

        bloquear("ESPACIO", espacio.getId(), null, dia.plusHours(9).plusMinutes(10), dia.plusHours(13), null)
                .andExpect(status().isBadRequest());
        bloquear("ESPACIO", espacio.getId(), null, ahora.minusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0),
                ahora.minusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0), null)
                .andExpect(status().isBadRequest());
        bloquear("ESPACIO", espacio.getId(), null, dia, dia.plusDays(40), null)
                .andExpect(status().isBadRequest());
        bloquear("ESPACIO", null, null, dia.plusHours(9), dia.plusHours(10), null)
                .andExpect(status().isBadRequest());
        bloquear("PISO", null, 999_999L, dia.plusHours(9), dia.plusHours(10), null)
                .andExpect(status().isBadRequest());
        bloquear("ESPACIO", 999_999L, null, dia.plusHours(9), dia.plusHours(10), null)
                .andExpect(status().isNotFound());
    }

    private ResultActions bloquear(String alcance, Long espacioId, Long pisoId, LocalDateTime inicio, LocalDateTime fin,
                                   Boolean cancelarReservas) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("alcance", alcance);
        cuerpo.put("espacioId", espacioId);
        cuerpo.put("pisoId", pisoId);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", fin.toString());
        cuerpo.put("motivo", "MANTENIMIENTO");
        cuerpo.put("detalle", "Pintura de paredes");
        cuerpo.put("cancelarReservas", cancelarReservas);
        return mvc.perform(post("/api/admin/bloqueos").with(comoAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
