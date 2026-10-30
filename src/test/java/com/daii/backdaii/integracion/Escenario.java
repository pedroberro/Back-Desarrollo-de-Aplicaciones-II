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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test data written straight to the database. Every booking gets its own one-hour slot in the
 * same space, so the database's no-overlap constraint never trips between tests.
 */
class Escenario {

    /** Seeded by V10: the only administrator of a fresh database. */
    static final String ADMIN = "admin@plantalibre.local";

    private static final AtomicInteger FRANJAS = new AtomicInteger();

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

    /** A booking still to come, stored in {@code estado}. */
    Reserva futura(Usuario cliente, EstadoReserva estado) {
        LocalDateTime inicio = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS)
                .plusDays(60).plusHours(2L * FRANJAS.incrementAndGet());
        return guardar(cliente, estado, inicio);
    }

    /** An active booking whose end already passed: reported as FINALIZADA. */
    Reserva terminada(Usuario cliente) {
        LocalDateTime inicio = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.HOURS)
                .minusDays(60).plusHours(2L * FRANJAS.incrementAndGet());
        return guardar(cliente, EstadoReserva.CONFIRMADA, inicio);
    }

    EstadoReserva estadoGuardado(Long reservaId) {
        return reservas.findById(reservaId).orElseThrow().getEstado();
    }

    private Reserva guardar(Usuario cliente, EstadoReserva estado, LocalDateTime inicio) {
        return reservas.save(Reserva.builder()
                .usuario(cliente)
                .creadaPor(cliente)
                .espacio(espacio())
                .inicio(inicio)
                .fin(inicio.plusHours(1))
                .estado(estado)
                .build());
    }

    private Espacio espacio() {
        return espacios.findAll(PageRequest.of(0, 1, Sort.by("id"))).getContent().get(0);
    }
}
