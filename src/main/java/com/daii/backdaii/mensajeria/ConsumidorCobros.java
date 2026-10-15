package com.daii.backdaii.mensajeria;

import com.daii.backdaii.service.CobroService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumer of the charge queue. A thrown exception rolls the charge back and the message is
 * retried (spring.rabbitmq.listener.simple.retry); when the retries run out it goes to the DLQ.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class ConsumidorCobros {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorCobros.class);

    private final CobroService cobros;

    @RabbitListener(queues = Colas.COBROS)
    public void recibir(CobroSolicitado mensaje) {
        log.info("Procesando el cobro de la reserva R-{} (solicitado el {})", mensaje.reservaId(), mensaje.solicitadoEl());
        cobros.procesar(mensaje.reservaId());
    }
}
