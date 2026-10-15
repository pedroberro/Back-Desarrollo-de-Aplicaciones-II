package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.evento.ReservaCreada;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
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
    private final Clock reloj;

    @TransactionalEventListener
    public void alCrearReserva(ReservaCreada evento) {
        try {
            rabbit.convertAndSend(Colas.EXCHANGE_RESERVAS, Colas.COBRO_SOLICITADO,
                    new CobroSolicitado(evento.reservaId(), Instant.now(reloj)));
            log.info("Cobro solicitado para la reserva R-{}", evento.reservaId());
        } catch (AmqpException e) {
            // The booking is already saved: it stays PENDIENTE and an administrator can confirm it.
            log.error("No se pudo publicar el cobro de la reserva R-{}: {}", evento.reservaId(), e.getMessage());
        }
    }
}
