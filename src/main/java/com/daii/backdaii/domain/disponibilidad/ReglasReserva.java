package com.daii.backdaii.domain.disponibilidad;

import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Terms of use every booking must meet, whoever creates it. The same values reach the client
 * through {@link #condiciones()}, so the "Condiciones de uso" box cannot drift from the rules.
 */
@Component
public class ReglasReserva {

    public static final Duration MINIMA = Duration.ofMinutes(30);
    public static final Duration MAXIMA = Duration.ofHours(8);
    public static final int ANTICIPACION_MAXIMA_DIAS = 60;

    public record Condiciones(int pasoMinutos, int minimaMinutos, int maximaMinutos, int anticipacionMaximaDias) {
    }

    public Condiciones condiciones() {
        return new Condiciones((int) MotorDisponibilidad.PASO.toMinutes(), (int) MINIMA.toMinutes(),
                (int) MAXIMA.toMinutes(), ANTICIPACION_MAXIMA_DIAS);
    }

    public void validar(Franja franja, Horario horario, LocalDateTime ahora) {
        if (!alineado(franja.inicio()) || !alineado(franja.fin())) {
            throw new BadRequestException("Los horarios van en bloques de 30 minutos (por ejemplo 10:00 o 10:30)", "FRANJA_INVALIDA");
        }
        if (!franja.fin().toLocalDate().equals(franja.inicio().toLocalDate())) {
            throw new BadRequestException("La reserva tiene que empezar y terminar el mismo día", "FRANJA_INVALIDA");
        }
        if (franja.duracion().compareTo(MINIMA) < 0 || franja.duracion().compareTo(MAXIMA) > 0) {
            throw new BadRequestException("La reserva dura entre 30 minutos y 8 horas", "FRANJA_INVALIDA");
        }
        if (!horario.abre(franja.inicio().toLocalDate())) {
            throw new BadRequestException("Los edificios abren de lunes a viernes", "FUERA_DE_HORARIO");
        }
        if (!horario.contiene(franja)) {
            throw new BadRequestException("El edificio abre de " + horario.apertura() + " a " + horario.cierre()
                    + ": la reserva tiene que quedar dentro de ese horario", "FUERA_DE_HORARIO");
        }
        if (franja.inicio().isBefore(ahora)) {
            throw new BadRequestException("No se puede reservar una franja que ya empezó", "FRANJA_PASADA");
        }
        if (franja.inicio().toLocalDate().isAfter(ahora.toLocalDate().plusDays(ANTICIPACION_MAXIMA_DIAS))) {
            throw new BadRequestException("Se puede reservar con hasta " + ANTICIPACION_MAXIMA_DIAS + " días de anticipación", "FRANJA_INVALIDA");
        }
    }

    /** Pending or confirmed bookings can be cancelled until they start. */
    public boolean cancelable(Reserva reserva, LocalDateTime ahora) {
        return reserva.getEstado().ocupa() && reserva.getInicio().isAfter(ahora);
    }

    private static boolean alineado(LocalDateTime t) {
        return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % MotorDisponibilidad.PASO.toMinutes() == 0;
    }
}
