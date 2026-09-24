package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.MotorDisponibilidad;
import com.daii.backdaii.domain.disponibilidad.Ocupacion;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.FranjaDTO;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.FranjaTomadaException;
import com.daii.backdaii.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one way to take a slot of a space, shared by customer bookings, admin bookings and blocks:
 * lock the space row, ask the engine against fresh data, and only then write. The lock makes
 * concurrent requests for the same space queue up; the database constraint catches anything
 * that could still slip through.
 */
@Component
@RequiredArgsConstructor
class TomaDeFranja {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EspacioRepository espacioRepository;
    private final Ocupaciones ocupaciones;
    private final MotorDisponibilidad motor;

    /** Must run inside a transaction: the lock lasts until it ends. */
    Espacio bloquear(Long espacioId) {
        return espacioRepository.findParaReservarById(espacioId)
                .orElseThrow(() -> new NotFoundException("Espacio no encontrado"));
    }

    static Franja franja(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new BadRequestException("El horario de fin tiene que ser posterior al de inicio", "FRANJA_INVALIDA");
        }
        return new Franja(inicio, fin);
    }

    /** Throws with the nearest free ranges when something else takes part of the slot. */
    void verificarLibre(Espacio espacio, Franja franja, Long reservaIgnorada, LocalDateTime ahora) {
        List<Ocupacion> delEspacio = Ocupaciones.de(ocupaciones.porEspacio(List.of(espacio.getId()),
                franja.inicio().toLocalDate().atStartOfDay(), franja.fin().toLocalDate().plusDays(1).atStartOfDay()),
                espacio.getId());
        List<Ocupacion> otras = delEspacio.stream()
                .filter(o -> reservaIgnorada == null || !o.esReserva(reservaIgnorada))
                .toList();

        motor.conflicto(otras, franja, null).ifPresent(conflicto -> {
            String que = conflicto.tipo() == Ocupacion.Tipo.BLOQUEO ? "está bloqueado" : "ya está reservado";
            List<FranjaDTO> alternativas = motor.huecosCercanos(otras, franja.inicio().toLocalDate(),
                            espacio.getPiso().getEdificio().getHorario(), ahora, franja.duracion(), franja.inicio(), 3)
                    .stream().map(FranjaDTO::de).toList();
            throw new FranjaTomadaException(espacio.getCodigo() + " " + que + " entre las "
                    + conflicto.inicio().format(HORA) + " y las " + conflicto.fin().format(HORA)
                    + ", que se superpone con el horario elegido.", alternativas);
        });
    }

    /** Runs the write and turns the exclusion constraint, if it fires, into the same 409. */
    <T> T guardar(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DataIntegrityViolationException e) {
            throw new FranjaTomadaException("Esa franja se ocupó recién. Elegí otro horario.", List.of());
        }
    }
}
