package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(jsonPath("$.components.schemas.Problema.properties.detail").exists())
                .andExpect(jsonPath("$.paths['/api/admin/reservas/{id}'].put.responses['409'].content['application/problem+json']").exists());
    }

    @Test
    void losErroresSiguenProblemDetails() throws Exception {
        mvc.perform(get("/api/empresas/{id}", 999_999L).with(como("cualquiera@pruebas.local", Rol.CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/empresas/999999"))
                // The front keeps reading message.
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void losErroresDeSeguridadTambienSiguenProblemDetails() throws Exception {
        mvc.perform(get("/api/empresas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/empresas"));
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

    /** Found by Schemathesis: a blank id in the URL answered 500. */
    @Test
    void unIdVacioEnLaRutaEsUnPedidoInvalido() throws Exception {
        mvc.perform(get("/api/espacios/{id}", " ").with(como("cualquiera@pruebas.local", Rol.CLIENTE)))
                .andExpect(status().isBadRequest());
    }

    /** Found by Schemathesis: a huge page number overflowed the query offset and answered 500. */
    @Test
    void unaPaginaEnormeNoEsUnErrorDelServidor() throws Exception {
        mvc.perform(get("/api/admin/reservas").with(comoAdmin()).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/admin/auditoria").with(comoAdmin()).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void losHorariosSeDocumentanComoTexto() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.LocalTime").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EdificioRef.properties.apertura.type").value("string"));
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
