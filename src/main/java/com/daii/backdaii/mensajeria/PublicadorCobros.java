package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.evento.CobroReencolado;
import com.daii.backdaii.domain.evento.ReservaCreada;
import com.daii.backdaii.service.impl.BarridoCobros;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;

/**
 * Producer: turns the ReservaCreada domain event into a message on the charge queue. It runs
 * after the booking commits, so the consumer always finds the row.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class PublicadorCobros {

    private static final Logger log = LoggerFactory.getLogger(PublicadorCobros.class);

    private final RabbitTemplate rabbit;
    private final BarridoCobros barrido;
    private final Clock reloj;

    /** Still inside the booking's transaction: from now on the sweep watches this charge. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void registrarSolicitud(ReservaCreada evento) {
        barrido.registrarSolicitud(evento.reservaId());
    }

    @TransactionalEventListener
    public void alCrearReserva(ReservaCreada evento) {
        publicar(evento.reservaId(), "Cobro solicitado");
    }

    @TransactionalEventListener
    public void alReencolar(CobroReencolado evento) {
        publicar(evento.reservaId(), "Cobro reencolado (intento " + evento.intento() + ")");
    }

    private void publicar(Long reservaId, String que) {
        try {
            rabbit.convertAndSend(Colas.EXCHANGE_RESERVAS, Colas.COBRO_SOLICITADO,
                    new CobroSolicitado(reservaId, Instant.now(reloj)));
            log.info("{} para la reserva R-{}", que, reservaId);
        } catch (AmqpException e) {
            // The booking is already saved and PENDIENTE: the sweep requests the charge again later.
            log.error("No se pudo publicar el cobro de la reserva R-{}: {}", reservaId, e.getMessage());
        }
    }
}
