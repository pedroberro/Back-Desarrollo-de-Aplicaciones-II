package com.daii.backdaii.domain.disponibilidad;

import java.time.Duration;
import java.time.LocalDateTime;

/** Half-open time range [inicio, fin): a slot ending at 11:00 does not touch one starting at 11:00. */
public record Franja(LocalDateTime inicio, LocalDateTime fin) {

    public Franja {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new IllegalArgumentException("Una franja termina después de empezar");
        }
    }

    public boolean seSuperponeCon(LocalDateTime otroInicio, LocalDateTime otroFin) {
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    public boolean seSuperponeCon(Franja otra) {
        return seSuperponeCon(otra.inicio, otra.fin);
    }

    public boolean contiene(Franja otra) {
        return !otra.inicio.isBefore(inicio) && !otra.fin.isAfter(fin);
    }

    public Duration duracion() {
        return Duration.between(inicio, fin);
    }
}
