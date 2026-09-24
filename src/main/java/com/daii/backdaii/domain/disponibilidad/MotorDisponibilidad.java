package com.daii.backdaii.domain.disponibilidad;

import com.daii.backdaii.domain.enums.EstadoEspacio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Availability engine. Pure and stateless: it gets the occupations of one space and answers
 * questions about time. Loading them is the service's job, so the rules are testable without
 * a database and the plan, the calendar and the booking validation all share one definition
 * of "free".
 */
@Component
public class MotorDisponibilidad {

    /** Slots start at :00 or :30. */
    public static final Duration PASO = Duration.ofMinutes(30);

    /**
     * First occupation that takes part of the slot, ignoring one booking so editing a booking
     * never collides with itself.
     */
    public Optional<Ocupacion> conflicto(Collection<Ocupacion> ocupaciones, Franja franja, Long reservaIgnorada) {
        return ocupaciones.stream()
                .filter(o -> reservaIgnorada == null || !o.esReserva(reservaIgnorada))
                .filter(o -> franja.seSuperponeCon(o.inicio(), o.fin()))
                .min(Comparator.comparing(Ocupacion::inicio));
    }

    /** Occupation that explains the state of the slot: a block wins over a booking. */
    public Optional<Ocupacion> ocupacionEn(Collection<Ocupacion> ocupaciones, Franja franja) {
        return ocupaciones.stream()
                .filter(o -> franja.seSuperponeCon(o.inicio(), o.fin()))
                .min(Comparator.comparing((Ocupacion o) -> o.tipo() == Ocupacion.Tipo.BLOQUEO ? 0 : 1)
                        .thenComparing(Ocupacion::inicio));
    }

    public EstadoEspacio estado(Collection<Ocupacion> ocupaciones, Franja franja) {
        return ocupacionEn(ocupaciones, franja)
                .map(o -> o.tipo() == Ocupacion.Tipo.BLOQUEO ? EstadoEspacio.BLOQUEADO : EstadoEspacio.OCUPADO)
                .orElse(EstadoEspacio.LIBRE);
    }

    /** Maximal free ranges inside the window, in order. */
    public List<Franja> huecos(Collection<Ocupacion> ocupaciones, Franja ventana) {
        List<Ocupacion> ordenadas = ocupaciones.stream()
                .filter(o -> ventana.seSuperponeCon(o.inicio(), o.fin()))
                .sorted(Comparator.comparing(Ocupacion::inicio))
                .toList();

        List<Franja> huecos = new ArrayList<>();
        LocalDateTime cursor = ventana.inicio();
        for (Ocupacion o : ordenadas) {
            if (o.inicio().isAfter(cursor)) {
                huecos.add(new Franja(cursor, o.inicio()));
            }
            if (o.fin().isAfter(cursor)) {
                cursor = o.fin();
            }
        }
        if (cursor.isBefore(ventana.fin())) {
            huecos.add(new Franja(cursor, ventana.fin()));
        }
        return huecos;
    }

    /**
     * Free ranges of one day that can still be booked: inside opening hours and not before the
     * next slot boundary. Empty when the building already closed.
     */
    public List<Franja> huecosDelDia(Collection<Ocupacion> ocupaciones, LocalDate fecha, Horario horario, LocalDateTime ahora) {
        if (!horario.abre(fecha)) {
            return List.of();
        }
        LocalDateTime desde = max(horario.aperturaEn(fecha), redondearArriba(ahora));
        LocalDateTime hasta = horario.cierreEn(fecha);
        if (!desde.isBefore(hasta)) {
            return List.of();
        }
        return huecos(ocupaciones, new Franja(desde, hasta));
    }

    /**
     * First free range of at least {@code minima} that starts at {@code desde} or later,
     * looking ahead up to {@code dias} days. Feeds "Próximo horario libre".
     */
    public Optional<Franja> proximoHueco(Collection<Ocupacion> ocupaciones, LocalDateTime desde, Horario horario,
                                         Duration minima, int dias) {
        LocalDateTime inicio = redondearArriba(desde);
        for (int d = 0; d < dias; d++) {
            LocalDate fecha = inicio.toLocalDate().plusDays(d);
            for (Franja hueco : huecosDelDia(ocupaciones, fecha, horario, inicio)) {
                if (hueco.duracion().compareTo(minima) >= 0) {
                    return Optional.of(hueco);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Free ranges of the day long enough for {@code duracion}, closest first to the time the
     * customer wanted. Offered when the slot was taken while confirming.
     */
    public List<Franja> huecosCercanos(Collection<Ocupacion> ocupaciones, LocalDate fecha, Horario horario,
                                       LocalDateTime ahora, Duration duracion, LocalDateTime referencia, int limite) {
        return huecosDelDia(ocupaciones, fecha, horario, ahora).stream()
                .filter(h -> h.duracion().compareTo(duracion) >= 0)
                .sorted(Comparator.comparing((Franja h) -> distancia(h, referencia)))
                .limit(limite)
                .sorted(Comparator.comparing(Franja::inicio))
                .toList();
    }

    /**
     * First start time, at or after {@code desde} on the same day, where a slot of {@code duracion}
     * is free for at least one of the spaces. Feeds "Probar 11:30 – 13:30" on a full floor.
     */
    public Optional<Franja> proximaFranjaConLibres(Collection<? extends Collection<Ocupacion>> porEspacio,
                                                   LocalDateTime desde, Duration duracion, Horario horario) {
        LocalDateTime inicio = redondearArriba(desde);
        if (!horario.abre(inicio.toLocalDate())) {
            return Optional.empty();
        }
        LocalDateTime cierre = horario.cierreEn(inicio.toLocalDate());
        for (LocalDateTime t = inicio; !t.plus(duracion).isAfter(cierre); t = t.plus(PASO)) {
            Franja candidata = new Franja(t, t.plus(duracion));
            boolean hayLibre = porEspacio.stream().anyMatch(ocupaciones -> conflicto(ocupaciones, candidata, null).isEmpty());
            if (hayLibre) {
                return Optional.of(candidata);
            }
        }
        return Optional.empty();
    }

    /** Next :00 or :30 boundary, or the same instant when it already is one. */
    public static LocalDateTime redondearArriba(LocalDateTime t) {
        LocalDateTime base = t.withSecond(0).withNano(0);
        if (base.isBefore(t)) {
            base = base.plusMinutes(1);
        }
        int resto = base.getMinute() % 30;
        return resto == 0 ? base : base.plusMinutes(30 - resto);
    }

    private static Duration distancia(Franja hueco, LocalDateTime referencia) {
        if (hueco.inicio().isAfter(referencia)) {
            return Duration.between(referencia, hueco.inicio());
        }
        if (!hueco.fin().isAfter(referencia)) {
            return Duration.between(hueco.fin(), referencia);
        }
        return Duration.ZERO;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
