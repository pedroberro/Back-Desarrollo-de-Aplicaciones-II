package com.daii.backdaii.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * On startup, encrypts every sensitive value still in plaintext and re-encrypts the ones written
 * with an older key (SEC-09). After a key rotation one restart leaves every value under the new
 * key; with nothing to do it is a cheap query. Runs last, after the demo data is loaded.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class RecifradoCampos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecifradoCampos.class);

    /** Table and its encrypted columns. */
    static final Map<String, List<String>> CAMPOS = Map.of(
            "usuarios", List.of("telefono", "segundo_factor_secreto"),
            "medios_pago", List.of("titular", "vencimiento"));

    private final JdbcTemplate jdbc;
    private final CifradoCampos cifrado;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        String prefijo = "enc:" + cifrado.versionActual() + ":%";
        for (var tabla : CAMPOS.entrySet()) {
            for (String columna : tabla.getValue()) {
                List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, " + columna + " AS valor FROM " + tabla.getKey()
                        + " WHERE " + columna + " IS NOT NULL AND " + columna + " NOT LIKE ?", prefijo);
                for (Map<String, Object> fila : filas) {
                    String nuevo = cifrado.cifrar(cifrado.descifrar((String) fila.get("valor")));
                    jdbc.update("UPDATE " + tabla.getKey() + " SET " + columna + " = ? WHERE id = ?", nuevo, fila.get("id"));
                }
                total += filas.size();
            }
        }
        if (total > 0) {
            log.info("[cifrado] {} campos sensibles cifrados con la clave {}", total, cifrado.versionActual());
        }
    }
}
