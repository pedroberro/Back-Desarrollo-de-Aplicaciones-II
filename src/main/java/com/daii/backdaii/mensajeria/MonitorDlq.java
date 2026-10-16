package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Alerts when messages land in the dead-letter queue (SEC-08). Watches the queue depth instead of
 * consuming it, so the messages stay there to be inspected. Each new message raises one alert:
 * an ERROR in the log, an audit entry and, when ALERTAS_EMAIL is set, a mail to the team.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
class MonitorDlq {

    private static final Logger log = LoggerFactory.getLogger(MonitorDlq.class);

    private final AmqpAdmin admin;
    private final AuditoriaService auditoria;
    private final EmailService emailService;
    private final String emailAlertas;
    private final AtomicLong ultimaCantidad = new AtomicLong(0);

    MonitorDlq(AmqpAdmin admin, AuditoriaService auditoria, EmailService emailService,
               @Value("${app.alertas.email:}") String emailAlertas) {
        this.admin = admin;
        this.auditoria = auditoria;
        this.emailService = emailService;
        this.emailAlertas = emailAlertas;
    }

    @Scheduled(initialDelayString = "${app.mensajeria.dlq.revisar-cada-ms:30000}",
            fixedDelayString = "${app.mensajeria.dlq.revisar-cada-ms:30000}")
    public void revisar() {
        QueueInformation info;
        try {
            info = admin.getQueueInfo(Colas.COBROS_DLQ);
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar la DLQ: {}", e.getMessage());
            return;
        }
        long cantidad = info == null ? 0 : info.getMessageCount();
        long anterior = ultimaCantidad.getAndSet(cantidad);
        if (cantidad > anterior) {
            alertar(cantidad - anterior, cantidad);
        }
    }

    void alertar(long nuevos, long total) {
        String detalle = nuevos + " mensaje(s) nuevo(s) en " + Colas.COBROS_DLQ + " (" + total + " en total)";
        log.error("ALERTA: {}. Revisar los cobros que no se pudieron procesar.", detalle);
        auditoria.registrar(EventoAuditoria.MENSAJE_EN_DLQ, ResultadoAuditoria.FALLO, null, detalle);
        if (!emailAlertas.isBlank()) {
            emailService.sendAlerta(emailAlertas, "Mensajes en la cola de mensajes muertos",
                    detalle + ".\n\nSon cobros que agotaron los reintentos o llegaron con firma inválida. "
                            + "Se pueden revisar en la consola de RabbitMQ.");
        }
    }
}
