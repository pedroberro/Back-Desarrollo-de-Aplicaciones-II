package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The published OpenAPI contract and the answers to malformed requests. */
class ContratoApiIntegracionTest extends IntegracionBase {

    @Autowired
    private MockMvc mvc;

    @Test
    void elContratoSePublicaSinAutenticacion() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/reservas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/reservas/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists());
    }

    @Test
    void unParametroObligatorioFaltanteEsUnPedidoInvalido() throws Exception {
        mvc.perform(get("/api/admin/agenda").with(comoAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unCuerpoQueNoEsJsonSeRechaza() throws Exception {
        mvc.perform(post("/api/reservas").with(como("cualquiera@pruebas.local", Rol.CLIENTE))
                        .contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }

    /** Found by Schemathesis: a multipart Content-Type without its boundary answered 500. */
    @Test
    void unPedidoMultipartNoEsUnErrorDelServidor() throws Exception {
        mvc.perform(get("/api/health").contentType("multipart/form-data"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reservas").with(como("cualquiera@pruebas.local", Rol.CLIENTE))
                        .contentType("multipart/form-data").content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unaRespuestaQueElClienteNoAceptaNoTraeCuerpo() throws Exception {
        mvc.perform(get("/api/empresas").with(como("cualquiera@pruebas.local", Rol.CLIENTE))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }
}
