package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.MedioPago;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A customer booking through the API: the rules, the conflicts and their own list. */
class ReservasClienteIntegracionTest extends IntegracionBase {

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
    private Espacio espacio;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
        espacio = escenario.espacio(Escenario.ESPACIO_API);
    }

    @Test
    void reservaUnaFranjaLibreYQuedaPendiente() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();

        JsonNode creada = json.readTree(reservar(cliente, inicio, inicio.plusHours(1), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.duracionMinutos").value(60))
                .andReturn().getResponse().getContentAsString());

        long id = creada.get("id").asLong();
        assertThat(escenario.estadoGuardado(id)).isEqualTo(EstadoReserva.PENDIENTE);
        mvc.perform(get("/api/reservas/mias").with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximas[0].id").value(id))
                .andExpect(jsonPath("$.historial").isEmpty());
        mvc.perform(get("/api/reservas/{id}", id).with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("R-" + id))
                .andExpect(jsonPath("$.cancelable").value(true))
                .andExpect(jsonPath("$.reintegroSiCancela.porcentaje").isNumber());
    }

    @Test
    void conUnaTarjetaPropiaLaReservaLaGuarda() throws Exception {
        MedioPago tarjeta = escenario.tarjeta(cliente, "4242");
        LocalDateTime inicio = escenario.franjaReservable();

        long id = json.readTree(reservar(cliente, inicio, inicio.plusHours(1), tarjeta.getId())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(reservas.findById(id).orElseThrow().getMedioPago().getId()).isEqualTo(tarjeta.getId());
    }

    @Test
    void conUnaTarjetaAjenaRespondeError() throws Exception {
        MedioPago ajena = escenario.tarjeta(escenario.cliente(), "4242");
        LocalDateTime inicio = escenario.franjaReservable();

        reservar(cliente, inicio, inicio.plusHours(1), ajena.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIO_PAGO_INVALIDO"));
    }

    @Test
    void unaFranjaTomadaRespondeConflictoConAlternativas() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        escenario.reservaEn(escenario.cliente(), espacio, inicio, EstadoReserva.CONFIRMADA);

        reservar(cliente, inicio, inicio.plusHours(1), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRANJA_OCUPADA"))
                .andExpect(jsonPath("$.alternativas").isNotEmpty());
    }

    @Test
    void elClienteNoPuedeEstarEnDosEspaciosALaVez() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        escenario.reservaEn(cliente, escenario.espacio(Escenario.ESPACIO_DIRECTO), inicio, EstadoReserva.CONFIRMADA);

        reservar(cliente, inicio, inicio.plusHours(1), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVA_SUPERPUESTA"));
    }

    @Test
    void lasReglasDeLaFranjaSeValidan() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        LocalDate sabado = inicio.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        LocalDateTime ahora = LocalDateTime.now(reloj);

        reservar(cliente, inicio.plusMinutes(15), inicio.plusMinutes(75), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRANJA_INVALIDA"));
        reservar(cliente, inicio, inicio.plusMinutes(9 * 60), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRANJA_INVALIDA"));
        reservar(cliente, sabado.atTime(10, 0), sabado.atTime(11, 0), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUERA_DE_HORARIO"));
        reservar(cliente, inicio.toLocalDate().atTime(5, 0), inicio.toLocalDate().atTime(6, 0), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUERA_DE_HORARIO"));
        LocalDate lejos = ahora.toLocalDate().plusDays(90).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        reservar(cliente, lejos.atTime(10, 0), lejos.atTime(11, 0), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRANJA_INVALIDA"));
        LocalDate pasado = ahora.toLocalDate().minusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        reservar(cliente, pasado.atTime(10, 0), pasado.atTime(11, 0), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRANJA_PASADA"));
    }

    @Test
    void sinEspacioElPedidoEsInvalido() throws Exception {
        mvc.perform(post("/api/reservas").with(como(cliente.getEmail(), Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"2030-01-01T10:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.espacioId").exists());
    }

    @Test
    void laReservaDeOtroClienteNoSeVe() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        long ajena = escenario.reservaEn(escenario.cliente(), espacio, inicio, EstadoReserva.PENDIENTE).getId();

        mvc.perform(get("/api/reservas/{id}", ajena).with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/reservas/{id}/cancelar", ajena).with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/reservas/{id}", ajena).with(comoAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void lasReservasPasadasYCanceladasVanAlHistorial() throws Exception {
        long terminada = escenario.terminada(cliente).getId();
        long cancelada = escenario.futura(cliente, EstadoReserva.CANCELADA).getId();

        JsonNode mias = json.readTree(mvc.perform(get("/api/reservas/mias").with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(mias.get("proximas")).isEmpty();
        assertThat(mias.get("historial").findValuesAsText("estado")).containsExactlyInAnyOrder("FINALIZADA", "CANCELADA");
        assertThat(mias.get("historial").findValues("id")).extracting(JsonNode::asLong).contains(terminada, cancelada);
    }

    private ResultActions reservar(Usuario quien, LocalDateTime inicio, LocalDateTime fin, Long medioPagoId) throws Exception {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("espacioId", espacio.getId());
        pedido.put("inicio", inicio.toString());
        pedido.put("fin", fin.toString());
        pedido.put("medioPagoId", medioPagoId);
        return mvc.perform(post("/api/reservas").with(como(quien.getEmail(), Rol.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pedido)));
    }
}
