package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Usuario;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A customer's saved cards: only the last four digits are kept and there is always one main card. */
class MediosPagoIntegracionTest extends IntegracionBase {

    private static final String RUTA = "/api/usuarios/me/medios-pago";

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

    private Usuario cliente;

    @BeforeEach
    void preparar() {
        cliente = new Escenario(usuarios, reservas, espacios, medios, reloj).cliente();
    }

    @Test
    void laPrimeraTarjetaEsLaPrincipalYSoloGuardaLosUltimosCuatro() throws Exception {
        agregar("4111 1111 1111 1111", "12/35")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ultimos4").value("1111"))
                .andExpect(jsonPath("$.principal").value(true));

        assertThat(medios.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(cliente.getId()))
                .singleElement()
                .satisfies(m -> assertThat(m.getNumeroTarjetaMasked()).isEqualTo("**** **** **** 1111"));
    }

    @Test
    void cambiarLaPrincipalYBorrarlaPasaLaMarcaALaQueQueda() throws Exception {
        long primera = id(agregar("4111 1111 1111 1111", "12/35"));
        long segunda = id(agregar("5500 0000 0000 0004", "11/34"));

        mvc.perform(patch(RUTA + "/{id}/principal", segunda).with(cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(segunda))
                .andExpect(jsonPath("$[0].principal").value(true));

        mvc.perform(delete(RUTA + "/{id}", segunda).with(cliente())).andExpect(status().isOk());

        mvc.perform(get(RUTA).with(cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(primera))
                .andExpect(jsonPath("$[0].principal").value(true));
    }

    @Test
    void seActualizanElTitularYElVencimiento() throws Exception {
        long id = id(agregar("4111 1111 1111 1111", "12/35"));

        mvc.perform(put(RUTA + "/{id}", id).with(cliente()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titular", "NUEVO TITULAR", "vencimiento", "10/36"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titular").value("NUEVO TITULAR"))
                .andExpect(jsonPath("$.vencimiento").value("10/36"));

        mvc.perform(put(RUTA + "/{id}", id).with(cliente()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titular", "NUEVO TITULAR", "vencimiento", "01/20"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noSeAceptanTarjetasVencidasNiMasDeTres() throws Exception {
        agregar("4111 1111 1111 1111", "01/20").andExpect(status().isBadRequest());

        agregar("4111 1111 1111 1111", "12/35").andExpect(status().isCreated());
        agregar("5500 0000 0000 0004", "12/35").andExpect(status().isCreated());
        agregar("3400 0000 0000 009", "12/35").andExpect(status().isCreated());
        agregar("6011 0000 0000 0004", "12/35").andExpect(status().isBadRequest());
    }

    @Test
    void laTarjetaDeOtroNoSePuedeTocar() throws Exception {
        long id = id(agregar("4111 1111 1111 1111", "12/35"));
        String otro = new Escenario(usuarios, reservas, espacios, medios, reloj).cliente().getEmail();

        mvc.perform(patch(RUTA + "/{id}/principal", id).with(como(otro, Rol.CLIENTE))).andExpect(status().isNotFound());
        mvc.perform(delete(RUTA + "/{id}", id).with(como(otro, Rol.CLIENTE))).andExpect(status().isNotFound());
        mvc.perform(put(RUTA + "/{id}", id).with(como(otro, Rol.CLIENTE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titular", "X", "vencimiento", "10/36"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unNumeroMalFormadoEsUnPedidoInvalido() throws Exception {
        agregar("1234", "12/35")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.numeroTarjeta").exists());
    }

    private ResultActions agregar(String numero, String vencimiento) throws Exception {
        return mvc.perform(post(RUTA).with(cliente()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("titular", "CLIENTE DE PRUEBA", "numeroTarjeta", numero,
                        "vencimiento", vencimiento, "codigoSeguridad", "123"))));
    }

    private long id(ResultActions resultado) throws Exception {
        JsonNode cuerpo = json.readTree(resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return cuerpo.get("id").asLong();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor cliente() {
        return como(cliente.getEmail(), Rol.CLIENTE);
    }
}
