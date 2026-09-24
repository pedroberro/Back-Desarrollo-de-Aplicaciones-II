package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Ocupacion;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.BloqueoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads what occupies each space (active bookings plus blocks) in two queries, already grouped
 * by space. It is the only bridge between the repositories and the availability engine.
 */
@Component
@RequiredArgsConstructor
class Ocupaciones {

    private final ReservaRepository reservaRepository;
    private final BloqueoRepository bloqueoRepository;

    Map<Long, List<Ocupacion>> porEspacio(Collection<Long> espacioIds, LocalDateTime desde, LocalDateTime hasta) {
        if (espacioIds.isEmpty()) {
            return Map.of();
        }
        List<Ocupacion> todas = new ArrayList<>(
                reservaRepository.findOcupaciones(espacioIds, desde, hasta, EstadoReserva.ACTIVOS));
        todas.addAll(bloqueoRepository.findOcupaciones(espacioIds, desde, hasta));
        return agrupar(todas);
    }

    /** Every space at once, for the listings that count free spaces across the catalog. */
    Map<Long, List<Ocupacion>> deTodos(LocalDateTime desde, LocalDateTime hasta) {
        List<Ocupacion> todas = new ArrayList<>(
                reservaRepository.findOcupacionesEntre(desde, hasta, EstadoReserva.ACTIVOS));
        todas.addAll(bloqueoRepository.findOcupacionesEntre(desde, hasta));
        return agrupar(todas);
    }

    static List<Ocupacion> de(Map<Long, List<Ocupacion>> porEspacio, Long espacioId) {
        return porEspacio.getOrDefault(espacioId, List.of());
    }

    private static Map<Long, List<Ocupacion>> agrupar(List<Ocupacion> ocupaciones) {
        Map<Long, List<Ocupacion>> porEspacio = new HashMap<>();
        for (Ocupacion o : ocupaciones) {
            porEspacio.computeIfAbsent(o.espacioId(), id -> new ArrayList<>()).add(o);
        }
        return porEspacio;
    }
}
