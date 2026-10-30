package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test data written straight to the database. Each kind of test works on its own space and time
 * window, so the database's no-overlap constraints never trip between tests:
 * <ul>
 *   <li>space 0, 60+ days ahead: bookings stored directly (transitions, repository)</li>
 *   <li>space 1, within the next 60 days, weekdays 09:00 to 17:00: bookings made through the API</li>
 *   <li>space 2, 300+ days ahead: blocks of a single space</li>
 * </ul>
 */
class Escenario {

    /** Seeded by V10: the only administrator of a fresh database. */
    static final String ADMIN = "admin@plantalibre.local";

    static final int ESPACIO_DIRECTO = 0;
    static final int ESPACIO_API = 1;
    static final int ESPACIO_BLOQUEOS = 2;

    private static final AtomicInteger FRANJAS_DIRECTAS = new AtomicInteger();
    private static final AtomicInteger FRANJAS_API = new AtomicInteger();
    private static final AtomicInteger DIAS_LEJANOS = new AtomicInteger();

    private final UsuarioRepository usuarios;
    private final ReservaRepository reservas;
    private final EspacioRepository espacios;
    private final MedioPagoRepository medios;
    private final Clock reloj;

    Escenario(UsuarioRepository usuarios, ReservaRepository reservas, EspacioRepository espacios,
              MedioPagoRepository medios, Clock reloj) {
        this.usuarios = usuarios;
        this.reservas = reservas;
        this.espacios = espacios;
        this.medios = medios;
        this.reloj = reloj;
    }

    Usuario cliente() {
        return usuarios.save(Usuario.builder()
                .nombre("Cliente de prueba")
                .email("cliente-" + UUID.randomUUID() + "@pruebas.plantalibre.local")
                .password("$2a$12$noSeUsaParaIniciarSesionEnEstasPruebasXXXXXXXXXXXXXXXX")
                .rol(Rol.CLIENTE)
                .build());
    }

    Usuario admin() {
        return usuarios.findByEmail(ADMIN).orElseThrow();
    }

    MedioPago tarjeta(Usuario dueno, String ultimos4) {
        return medios.save(MedioPago.builder()
                .usuario(dueno)
                .tipo("CREDITO")
                .descripcion("Tarjeta de prueba")
                .numeroTarjetaMasked("**** **** **** " + ultimos4)
                .titular("CLIENTE DE PRUEBA")
                .vencimiento("12/30")
                .verificado(true)
                .eliminado(false)
                .principal(true)
                .build());
    }

    Espacio espacio(int indice) {
        return espacios.findAll(PageRequest.of(indice, 1, Sort.by("id"))).getContent().get(0);
    }

    /** A booking still to come, stored in {@code estado}. */
    Reserva futura(Usuario cliente, EstadoReserva estado) {
        LocalDateTime inicio = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS)
                .plusDays(61).plusHours(2L * FRANJAS_DIRECTAS.incrementAndGet());
        return reservaEn(cliente, espacio(ESPACIO_DIRECTO), inicio, estado);
    }

    /** An active booking whose end already passed: reported as FINALIZADA. */
    Reserva terminada(Usuario cliente) {
        LocalDateTime inicio = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS)
                .minusDays(61).plusHours(2L * FRANJAS_DIRECTAS.incrementAndGet());
        return reservaEn(cliente, espacio(ESPACIO_DIRECTO), inicio, EstadoReserva.CONFIRMADA);
    }

    /**
     * Start of a free one-hour slot of {@link #ESPACIO_API} that the booking rules accept: a weekday
     * from the day after tomorrow on, between 09:00 and 17:00, within the 60 days allowed.
     */
    LocalDateTime franjaReservable() {
        int n = FRANJAS_API.getAndIncrement();
        LocalDate dia = LocalDate.now(reloj).plusDays(2);
        for (int habiles = 0; habiles < n / 8 || esFinDeSemana(dia); ) {
            if (!esFinDeSemana(dia)) {
                habiles++;
            }
            dia = dia.plusDays(1);
        }
        return dia.atTime(9 + n % 8, 0);
    }

    /** Midnight of a day far ahead, never used by another test: for blocks. */
    LocalDateTime diaLejano() {
        return LocalDate.now(reloj).plusDays(300L + 2L * DIAS_LEJANOS.incrementAndGet()).atStartOfDay();
    }

    Reserva reservaEn(Usuario cliente, Espacio espacio, LocalDateTime inicio, EstadoReserva estado) {
        return reservas.save(Reserva.builder()
                .usuario(cliente)
                .creadaPor(cliente)
                .espacio(espacio)
                .inicio(inicio)
                .fin(inicio.plusHours(1))
                .estado(estado)
                .build());
    }

    EstadoReserva estadoGuardado(Long reservaId) {
        return reservas.findById(reservaId).orElseThrow().getEstado();
    }

    private static boolean esFinDeSemana(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
