package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.enums.Rol;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Whole application against a real PostgreSQL in a container, migrated by Flyway exactly as in
 * production. The container starts once and is shared by every integration test class, so Spring
 * reuses the same context. Without Docker these tests are skipped; the CI always has it.
 *
 * The secrets below exist only for these tests: they pass the same checks as real ones (Secretos).
 */
@SpringBootTest(properties = {
        "app.jwt.secret=pruebas-integracion-jwt-Xk29Qm7Lr5Tv8Wz3Nb6Hc",
        "app.cifrado.clave-actual=pruebas-integracion-cifrado-Ye4Jn8Rp2Gd7Sk9Fw5",
        "spring.datasource.password=la-define-el-contenedor",
        "app.demo.habilitado=false",
        "app.seguridad.filtraciones.consultar-externo=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegracionBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Signed in as {@code email} with the same authorities the application gives the role. */
    static RequestPostProcessor como(String email, Rol rol) {
        List<SimpleGrantedAuthority> autoridades = Stream.concat(Stream.of("ROLE_" + rol.name()),
                        rol.getPermisos().stream().map(Enum::name))
                .map(SimpleGrantedAuthority::new)
                .toList();
        return user(email).authorities(autoridades);
    }

    static RequestPostProcessor comoAdmin() {
        return como(Escenario.ADMIN, Rol.ADMIN);
    }
}
