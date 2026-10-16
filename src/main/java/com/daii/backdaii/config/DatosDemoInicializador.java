package com.daii.backdaii.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Keeps the demo alive: a catalog without bookings shows every space free, which proves nothing.
 * On startup it fills the weekdays of the two weeks around today with bookings and a few blocks,
 * owned by demo customers ({@value #DOMINIO}). Days that already have demo data are skipped, so
 * restarts only add the days that entered the window. Real users' bookings are never touched:
 * the generator works around them.
 *
 * <p>Demo customers (e.g. ana.ruiz{@value #DOMINIO}) sign in with the password in DEMO_PASSWORD,
 * which never lives in the repository. Without it new demo accounts get a random password nobody
 * knows. Disabled with {@code app.demo.habilitado=false}.
 */
@Component
// Before RecifradoCampos, which encrypts the phone numbers this inserts in plaintext.
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@ConditionalOnProperty(name = "app.demo.habilitado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DatosDemoInicializador implements ApplicationRunner {

    public static final String DOMINIO = "@demo.plantalibre.local";

    private static final Logger log = LoggerFactory.getLogger(DatosDemoInicializador.class);
    private static final int DIAS_ATRAS = 14;
    private static final int DIAS_ADELANTE = 14;
    /** Share of Torre Callao bookings that go to Ana Ruiz, the account used to demo "Mis reservas". */
    private static final double PROBABILIDAD_ANA = 0.007;

    private static final List<String> CLIENTES = List.of(
            "Ana Ruiz", "Julián Rey", "Diego Sosa", "Lucía Paz", "Nadia Kim", "Sofía Luna", "Martín Ferro",
            "Pilar Otero", "Julieta Arce", "Juan Peralta", "Tomás Vega", "Camila Ortiz", "Bruno Díaz",
            "Valentina Gil", "Federico Luna", "Carla Mendez", "Ignacio Ríos", "Florencia Paz", "Santiago Roldán",
            "Micaela Sosa", "Lautaro Gómez", "Agustina Vera", "Matías Herrera", "Paula Castro", "Gonzalo Ibarra",
            "Rocío Molina", "Nicolás Funes", "Brenda Acosta", "Ezequiel Rojas", "Daniela Quiroga");

    private static final String[][] BLOQUEOS = {
            {"MANTENIMIENTO", "Mantenimiento eléctrico"},
            {"LIMPIEZA", "Limpieza profunda"},
            {"EVENTO", "Evento interno"},
            {"MANTENIMIENTO", "Cambio de mobiliario"},
    };

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    @Value("${app.demo.password:}")
    private String password;

    private record EspacioDemo(long id, boolean sala, boolean torreCallao, LocalTime apertura, LocalTime cierre) {
    }

    private record Intervalo(LocalDateTime inicio, LocalDateTime fin) {
        boolean choca(LocalDateTime otroInicio, LocalDateTime otroFin) {
            return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            sembrar();
        } catch (RuntimeException e) {
            // Demo data is a convenience: never let it stop the application.
            log.error("No se pudieron cargar los datos de demostración", e);
        }
    }

    private void sembrar() {
        Map<String, Long> clientes = asegurarClientes();
        Long adminId = jdbc.query("SELECT id FROM usuarios WHERE rol = 'ADMIN' ORDER BY id LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (adminId == null) {
            log.warn("[demo] No hay un administrador: no se cargan bloqueos de demostración");
        }
        List<EspacioDemo> espacios = jdbc.query("""
                SELECT e.id, e.tipo, ed.nombre, ed.hora_apertura, ed.hora_cierre
                FROM espacios e JOIN pisos p ON p.id = e.piso_id JOIN edificios ed ON ed.id = p.edificio_id
                ORDER BY e.id""",
                (rs, i) -> new EspacioDemo(rs.getLong(1), !"CUBICULO".equals(rs.getString(2)),
                        "Torre Callao".equals(rs.getString(3)), rs.getTime(4).toLocalTime(), rs.getTime(5).toLocalTime()));
        if (espacios.isEmpty()) {
            return;
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        int dias = 0;
        int reservas = 0;
        for (LocalDate dia = hoy.minusDays(DIAS_ATRAS); !dia.isAfter(hoy.plusDays(DIAS_ADELANTE)); dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek().getValue() >= 6 || yaTieneDemo(dia)) {
                continue;
            }
            LocalDate fecha = dia;
            reservas += transaccion.execute(estado -> sembrarDia(fecha, ahora, espacios, clientes, adminId));
            dias++;
        }
        if (dias > 0) {
            log.info("[demo] Cargadas {} reservas de demostración en {} días. Clientes demo: ana.ruiz{}",
                    reservas, dias, DOMINIO);
        }
    }

    private Map<String, Long> asegurarClientes() {
        String hash = null;
        Map<String, Long> ids = new HashMap<>();
        for (String nombre : CLIENTES) {
            String email = email(nombre);
            Long id = jdbc.query("SELECT id FROM usuarios WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, email);
            if (id == null) {
                if (hash == null) {
                    hash = passwordEncoder.encode(passwordDemo());
                }
                id = jdbc.queryForObject("""
                        INSERT INTO usuarios (nombre, email, telefono, password, rol, estado)
                        VALUES (?, ?, '+54 11 5555 0000', ?, 'CLIENTE', 'ACTIVO') RETURNING id""",
                        Long.class, nombre, email, hash);
            }
            ids.put(nombre, id);
        }
        return ids;
    }

    private boolean yaTieneDemo(LocalDate dia) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reservas r JOIN usuarios u ON u.id = r.usuario_id
                               WHERE u.email LIKE ? AND r.inicio >= ? AND r.inicio < ?)""",
                Boolean.class, "%" + DOMINIO, Timestamp.valueOf(dia.atStartOfDay()), Timestamp.valueOf(dia.plusDays(1).atStartOfDay()));
        return Boolean.TRUE.equals(existe);
    }

    private int sembrarDia(LocalDate dia, LocalDateTime ahora, List<EspacioDemo> espacios, Map<String, Long> clientes, Long adminId) {
        Map<Long, List<Intervalo>> ocupado = ocupacionesExistentes(dia);
        List<Object[]> reservas = new ArrayList<>();
        List<Object[]> bloqueos = new ArrayList<>();
        boolean pasado = dia.isBefore(ahora.toLocalDate());
        List<Long> pool = clientes.entrySet().stream().filter(e -> !e.getKey().equals("Ana Ruiz")).map(Map.Entry::getValue).toList();
        Long ana = clientes.get("Ana Ruiz");

        for (EspacioDemo espacio : espacios) {
            // Deterministic per space and day: the same demo every time the window is rebuilt.
            Random rnd = new Random(dia.toEpochDay() * 7_919L + espacio.id());
            List<Intervalo> tomados = ocupado.computeIfAbsent(espacio.id(), id -> new ArrayList<>());
            int apertura = espacio.apertura().getHour() * 2 + (espacio.apertura().getMinute() >= 30 ? 1 : 0);
            int cierre = espacio.cierre().getHour() * 2 + (espacio.cierre().getMinute() >= 30 ? 1 : 0);

            if (adminId != null && !pasado && rnd.nextDouble() < 0.02) {
                boolean todoElDia = rnd.nextBoolean();
                int desde = todoElDia ? apertura : apertura + 2 * rnd.nextInt(Math.max(1, (cierre - apertura) / 2 - 3));
                int hasta = todoElDia ? cierre : Math.min(cierre, desde + 2 + 2 * rnd.nextInt(3));
                Intervalo intervalo = new Intervalo(media(dia, desde), media(dia, hasta));
                if (tomados.stream().noneMatch(t -> t.choca(intervalo.inicio(), intervalo.fin()))) {
                    String[] bloqueo = BLOQUEOS[rnd.nextInt(BLOQUEOS.length)];
                    bloqueos.add(new Object[]{espacio.id(), ts(intervalo.inicio()), ts(intervalo.fin()), bloqueo[0], bloqueo[1],
                            UUID.randomUUID(), adminId});
                    tomados.add(intervalo);
                }
            }

            int intentos = espacio.sala() ? 1 + rnd.nextInt(3) : rnd.nextInt(3);
            for (int i = 0; i < intentos; i++) {
                int largo = espacio.sala() ? 2 + rnd.nextInt(5) : 2 + rnd.nextInt(7);
                int ultimoInicio = cierre - largo;
                if (ultimoInicio <= apertura) {
                    continue;
                }
                int desde = apertura + rnd.nextInt(ultimoInicio - apertura + 1);
                LocalDateTime inicio = media(dia, desde);
                LocalDateTime fin = media(dia, desde + largo);
                if (tomados.stream().anyMatch(t -> t.choca(inicio, fin))) {
                    continue;
                }
                boolean esDeAna = espacio.torreCallao() && rnd.nextDouble() < PROBABILIDAD_ANA;
                Long usuario = esDeAna ? ana : pool.get(rnd.nextInt(pool.size()));
                // Never PENDIENTE: that state means a charge in flight, which demo bookings never have.
                String estado = rnd.nextDouble() < (pasado ? 0.12 : 0.08) ? "CANCELADA" : "CONFIRMADA";
                LocalDateTime creada = inicio.minusDays(1 + rnd.nextInt(10)).minusHours(rnd.nextInt(8));
                reservas.add(new Object[]{espacio.id(), usuario, usuario, ts(inicio), ts(fin), estado, ts(creada), ts(creada)});
                if (!"CANCELADA".equals(estado)) {
                    tomados.add(new Intervalo(inicio, fin));
                }
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO reservas (espacio_id, usuario_id, creada_por_id, inicio, fin, estado, creada_el, actualizada_el)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", reservas);
        jdbc.batchUpdate("""
                INSERT INTO bloqueos (espacio_id, inicio, fin, motivo, detalle, grupo, creado_por_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", bloqueos);
        return reservas.size();
    }

    private Map<Long, List<Intervalo>> ocupacionesExistentes(LocalDate dia) {
        Map<Long, List<Intervalo>> ocupado = new HashMap<>();
        Timestamp desde = Timestamp.valueOf(dia.atStartOfDay());
        Timestamp hasta = Timestamp.valueOf(dia.plusDays(1).atStartOfDay());
        jdbc.query("""
                SELECT espacio_id, inicio, fin FROM reservas
                WHERE estado IN ('PENDIENTE', 'CONFIRMADA') AND inicio < ? AND fin > ?
                UNION ALL
                SELECT espacio_id, inicio, fin FROM bloqueos WHERE inicio < ? AND fin > ?""",
                rs -> {
                    ocupado.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>())
                            .add(new Intervalo(rs.getTimestamp(2).toLocalDateTime(), rs.getTimestamp(3).toLocalDateTime()));
                },
                hasta, desde, hasta, desde);
        return ocupado;
    }

    private static LocalDateTime media(LocalDate dia, int mediasHoras) {
        return dia.atStartOfDay().plusMinutes(30L * mediasHoras);
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    static String email(String nombre) {
        String base = java.text.Normalizer.normalize(nombre.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace(' ', '.');
        return base + DOMINIO;
    }

    /** DEMO_PASSWORD, or a random one when it is not set: demo accounts are never left with a known password. */
    private String passwordDemo() {
        if (password == null || password.isBlank()) {
            log.warn("[demo] DEMO_PASSWORD no está configurada: las cuentas demo nuevas quedan con una contraseña aleatoria");
            return java.util.UUID.randomUUID() + "Aa1";
        }
        return password;
    }
}
