package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class UsuariosPaginacionIntegracionTest extends IntegracionBase {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;

    @Test
    void b9a_devuelveDiezUsuariosYMetadatosDePaginacion() throws Exception {
        long totalAntes = usuarios.count();
        crearUsuarios(10);
        long total = usuarios.count();

        mvc.perform(get("/api/usuarios").with(comoAdmin()).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.totalPages").value((int) Math.ceil(total / 10.0)));
        assertThat(total).isEqualTo(totalAntes + 10);
    }

    @Test
    void b9b_paginaInexistenteDevuelve200ConContenidoVacio() throws Exception {
        mvc.perform(get("/api/usuarios").with(comoAdmin()).param("page", "100000").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(100000));
    }

    @Test
    void b9c_sizeMayorAlMaximoSeLimitaASinError() throws Exception {
        mvc.perform(get("/api/usuarios").with(comoAdmin()).param("page", "0").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    private void crearUsuarios(int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            usuarios.save(Usuario.builder()
                    .nombre("Usuario paginacion " + i)
                    .email("paginacion-" + UUID.randomUUID() + "@pruebas.plantalibre.local")
                    .password("$2a$12$noSeUsaParaIniciarSesionEnEstasPruebasXXXXXXXXXXXXXXXX")
                    .rol(Rol.CLIENTE)
                    .build());
        }
    }
}
