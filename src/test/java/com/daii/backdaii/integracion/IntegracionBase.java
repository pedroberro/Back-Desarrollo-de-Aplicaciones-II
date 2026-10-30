package com.daii.backdaii.integracion;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

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
}
