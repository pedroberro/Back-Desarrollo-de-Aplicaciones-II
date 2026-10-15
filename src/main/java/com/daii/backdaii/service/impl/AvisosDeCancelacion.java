package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.cobro.PoliticaCancelacion;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Tells the customer that a booking was cancelled, by whom, and what goes back to the card. */
@Component
@RequiredArgsConstructor
class AvisosDeCancelacion {

    private final EmailService emailService;

    @TransactionalEventListener
    public void alCancelar(ReservaCancelada c) {
        String reserva = c.codigoReserva() + " (" + c.descripcionReserva() + ")";
        String titulo;
        String cuerpo;
        switch (c.motivo()) {
            case CLIENTE -> {
                titulo = "Cancelaste tu reserva " + c.codigoReserva();
                cuerpo = "Cancelaste la reserva " + reserva + ".";
            }
            case ADMINISTRADOR -> {
                titulo = "Se canceló tu reserva " + c.codigoReserva();
                cuerpo = "Un administrador canceló tu reserva " + reserva + "." + motivo(c.detalle());
            }
            case ELIMINADA -> {
                titulo = "Se eliminó tu reserva " + c.codigoReserva();
                cuerpo = "Un administrador eliminó tu reserva " + reserva + " y la franja quedó libre." + motivo(c.detalle());
            }
            default -> {
                titulo = "Se canceló tu reserva " + c.codigoReserva();
                cuerpo = "El espacio de tu reserva " + reserva + " quedó bloqueado en ese horario ("
                        + c.detalle() + "), así que la reserva se canceló.";
            }
        }
        emailService.sendAvisoReserva(c.email(), c.nombre(), titulo, cuerpo + "\n\n" + reintegro(c));
    }

    private static String motivo(String detalle) {
        return detalle == null || detalle.isBlank() ? "" : "\nMotivo: " + detalle;
    }

    private static String reintegro(ReservaCancelada c) {
        if (c.cobrado() == null) {
            return "No se te había cobrado nada, así que no hay nada que devolver.";
        }
        if (c.reintegrado().compareTo(c.cobrado()) == 0) {
            return "Te devolvemos el total, " + pesos(c.reintegrado()) + ", a la tarjeta con la que pagaste.";
        }
        if (c.reintegrado().signum() > 0) {
            return "Según la política de cancelación, entre " + PoliticaCancelacion.SIN_CARGO.toHours() + " y "
                    + PoliticaCancelacion.CON_RETENCION.toHours() + " horas antes te devolvemos el " + c.porcentaje() + "%: "
                    + pesos(c.reintegrado()) + " de los " + pesos(c.cobrado()) + " que pagaste.";
        }
        return "Como cancelaste con menos de " + PoliticaCancelacion.CON_RETENCION.toHours()
                + " horas de anticipación, la política de cancelación no prevé reintegro.";
    }

    private static String pesos(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).format(monto);
    }
}
