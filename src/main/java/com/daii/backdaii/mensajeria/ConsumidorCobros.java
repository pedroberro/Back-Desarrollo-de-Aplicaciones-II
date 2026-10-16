package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.CobroService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumer of the charge queue. Checks the signature first (SEC-08): an unsigned or altered
 * message never reaches the charge and goes straight to the DLQ. A thrown exception during the
 * charge rolls it back and the message is retried; when the retries run out it goes to the DLQ.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class ConsumidorCobros {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorCobros.class);

    private final CobroService cobros;
    private final FirmaMensajes firma;
    private final MessageConverter conversor;
    private final AuditoriaService auditoria;

    @RabbitListener(queues = Colas.COBROS)
    public void recibir(Message mensaje) {
        String id = mensaje.getMessageProperties().getMessageId();
        if (!firma.valida(id, mensaje.getBody(), mensaje.getMessageProperties().getHeaders().get(FirmaMensajes.CABECERA))) {
            log.error("Mensaje {} con firma inválida: se descarta sin procesar", id);
            auditoria.registrar(EventoAuditoria.MENSAJE_FIRMA_INVALIDA, ResultadoAuditoria.DENEGADO, null,
                    "Cola " + Colas.COBROS + ", mensaje " + id);
            throw new AmqpRejectAndDontRequeueException("Firma inválida");
        }
        CobroSolicitado solicitud = (CobroSolicitado) conversor.fromMessage(mensaje);
        log.info("Procesando el cobro de la reserva R-{} (solicitado el {})", solicitud.reservaId(), solicitud.solicitadoEl());
        cobros.procesar(solicitud.reservaId(), id);
    }
}
