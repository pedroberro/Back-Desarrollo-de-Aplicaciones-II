package com.daii.backdaii.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Counters in the limites_intentos table. Each attempt is one atomic upsert, so concurrent
 * requests on different instances cannot both slip under the limit; the database clock decides
 * the windows, so instances with drifting clocks agree.
 */
@Component
@RequiredArgsConstructor
public class AlmacenIntentosEnBase implements AlmacenIntentos {

    private final JdbcTemplate jdbc;

    @Override
    public int sumar(String clave, Duration ventana) {
        return jdbc.queryForObject("""
                INSERT INTO limites_intentos AS l (clave, inicio, cantidad) VALUES (?, now(), 1)
                ON CONFLICT (clave) DO UPDATE SET
                    inicio = CASE WHEN l.inicio <= now() - (? * interval '1 millisecond') THEN now() ELSE l.inicio END,
                    cantidad = CASE WHEN l.inicio <= now() - (? * interval '1 millisecond') THEN 1 ELSE l.cantidad + 1 END
                RETURNING cantidad
                """, Integer.class, clave, ventana.toMillis(), ventana.toMillis());
    }

    @Override
    public int contar(String clave, Duration ventana) {
        List<Integer> cantidad = jdbc.queryForList("""
                SELECT cantidad FROM limites_intentos
                WHERE clave = ? AND inicio > now() - (? * interval '1 millisecond')
                """, Integer.class, clave, ventana.toMillis());
        return cantidad.isEmpty() ? 0 : cantidad.get(0);
    }

    @Override
    public void borrar(String clave) {
        jdbc.update("DELETE FROM limites_intentos WHERE clave = ?", clave);
    }

    @Override
    public int descartarVencidos(Duration antiguedad) {
        return jdbc.update("DELETE FROM limites_intentos WHERE inicio < now() - (? * interval '1 millisecond')",
                antiguedad.toMillis());
    }
}
