package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.PasarelaPagos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stands in for the payment provider, with the providers' sandbox test cards: one ending in 0002
 * (4000 0000 0000 0002) is declined, one ending in 0119 (4000 0000 0000 0119) makes the provider
 * fail technically, and every other card is approved. The delay mimics the provider's response
 * time, so the PENDIENTE state is visible in a demo.
 */
@Component
public class PasarelaPagosSimulada implements PasarelaPagos {

    static final String TERMINACION_RECHAZADA = "0002";
    static final String TERMINACION_FALLA_TECNICA = "0119";

    private final long demoraMs;

    public PasarelaPagosSimulada(@Value("${app.pasarela.demora-ms:1500}") long demoraMs) {
        this.demoraMs = demoraMs;
    }

    @Override
    public ResultadoPago cobrar(SolicitudPago solicitud) {
        esperar();
        if (TERMINACION_FALLA_TECNICA.equals(solicitud.ultimos4())) {
            throw new IllegalStateException("La pasarela de pagos no respondió");
        }
        if (TERMINACION_RECHAZADA.equals(solicitud.ultimos4())) {
            return ResultadoPago.rechazado("El emisor de la tarjeta rechazó el pago");
        }
        return ResultadoPago.aprobado("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /** Refunds go through without delay: the customer is waiting for the cancellation answer. */
    @Override
    public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
        return ResultadoPago.aprobado("SIM-R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private void esperar() {
        if (demoraMs <= 0) {
            return;
        }
        try {
            Thread.sleep(demoraMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera de la pasarela de pagos", e);
        }
    }
}
