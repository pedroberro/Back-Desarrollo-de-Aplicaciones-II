package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.evento.CobroReencolado;
import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.domain.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net of the asynchronous charge. A charge request can get lost (the broker was down when
 * the booking was saved) or end in the dead-letter queue (the provider kept failing). Either way
 * the booking would stay PENDIENTE forever, holding its slot. The sweep finds those bookings and
 * requests the charge again; after a few tries it gives up, cancels the booking and frees the slot.
 * Requesting twice is harmless: the consumer ignores a booking that is no longer pending.
 */
@Component
public class BarridoCobros {

    private static final Logger log = LoggerFactory.getLogger(BarridoCobros.class);
    private static final int POR_PASADA = 50;
    static final String MOTIVO_ABANDONO = "no pudimos procesar el pago después de varios intentos";

    private final ReservaRepository reservaRepository;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final long esperaSegundos;
    private final int maxReencolados;

    public record Resultado(int reencolados, int abandonados) {
    }

    public BarridoCobros(ReservaRepository reservaRepository, ApplicationEventPublisher eventos, Clock reloj,
                         @Value("${app.mensajeria.barrido.espera-segundos:120}") long esperaSegundos,
                         @Value("${app.mensajeria.barrido.max-reencolados:3}") int maxReencolados) {
        this.reservaRepository = reservaRepository;
        this.eventos = eventos;
        this.reloj = reloj;
        this.esperaSegundos = esperaSegundos;
        this.maxReencolados = maxReencolados;
    }

    /** Marks that the booking's charge entered the queue. Called in the transaction that creates it. */
    @Transactional
    public void registrarSolicitud(Long reservaId) {
        reservaRepository.findById(reservaId).ifPresent(r -> r.setCobroSolicitadoEl(LocalDateTime.now(reloj)));
    }

    /** One pass: requests again what waited too long, gives up on what already used its tries. */
    @Transactional
    public Resultado barrer() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Reserva> candidatas = reservaRepository.findCobrosSinResolver(EstadoReserva.PENDIENTE,
                ahora.minusSeconds(esperaSegundos), PageRequest.of(0, POR_PASADA));
        int reencolados = 0;
        int abandonados = 0;
        for (Reserva candidata : candidatas) {
            // Locked and read again: the charge or a cancellation may have landed meanwhile.
            Reserva reserva = reservaRepository.findBloqueadaById(candidata.getId()).orElse(null);
            if (reserva == null || reserva.getEstado() != EstadoReserva.PENDIENTE) {
                continue;
            }
            if (reserva.getReencoladosCobro() >= maxReencolados) {
                abandonar(reserva);
                abandonados++;
            } else {
                reserva.setReencoladosCobro(reserva.getReencoladosCobro() + 1);
                reserva.setCobroSolicitadoEl(ahora);
                eventos.publishEvent(new CobroReencolado(reserva.getId(), reserva.getReencoladosCobro()));
                reencolados++;
            }
        }
        if (reencolados + abandonados > 0) {
            log.info("Barrido de cobros: {} reencolados, {} abandonados", reencolados, abandonados);
        }
        return new Resultado(reencolados, abandonados);
    }

    private void abandonar(Reserva reserva) {
        reserva.cambiarEstado(EstadoReserva.CANCELADA);
        log.warn("Reserva {}: cobro sin respuesta tras {} reencolados, se cancela", reserva.getCodigo(), maxReencolados);
        // Same mail as a declined card: the booking is cancelled and the slot is free again.
        eventos.publishEvent(new CobroResuelto(reserva.getId(), reserva.getCodigo(),
                Vistas.describir(reserva.getEspacio(), new Franja(reserva.getInicio(), reserva.getFin())),
                reserva.getUsuario().getEmail(), reserva.getUsuario().getNombre(), null, null, false,
                MOTIVO_ABANDONO));
    }
}
