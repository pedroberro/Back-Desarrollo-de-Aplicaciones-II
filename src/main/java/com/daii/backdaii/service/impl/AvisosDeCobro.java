package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Tells the customer how the charge ended. After commit, so a rolled-back charge never mails. */
@Component
@RequiredArgsConstructor
class AvisosDeCobro {

    private final EmailService emailService;

    @TransactionalEventListener
    public void alResolverCobro(CobroResuelto cobro) {
        if (cobro.aprobado()) {
            emailService.sendAvisoReserva(cobro.email(), cobro.nombre(), "Tu reserva está confirmada",
                    "Cobramos " + pesos(cobro.monto()) + " a tu tarjeta terminada en " + cobro.ultimos4()
                            + " por la reserva " + cobro.codigoReserva() + ": " + cobro.descripcionReserva() + ".");
        } else {
            emailService.sendAvisoReserva(cobro.email(), cobro.nombre(), "No pudimos cobrar tu reserva",
                    "La reserva " + cobro.codigoReserva() + " (" + cobro.descripcionReserva() + ") quedó cancelada y el espacio se liberó. "
                            + "Motivo: " + cobro.motivoRechazo() + ". Revisá tus métodos de pago en tu perfil y volvé a reservar.");
        }
    }

    private static String pesos(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).format(monto);
    }
}
