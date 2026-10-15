package com.daii.backdaii.service;

import java.math.BigDecimal;

/**
 * Port to the payment provider. The app only knows this interface; the adapter decides how the
 * charge reaches the provider (today a simulator, see PasarelaPagosSimulada).
 */
public interface PasarelaPagos {

    /** Declines come back as a result; only technical failures throw. */
    ResultadoPago cobrar(SolicitudPago solicitud);

    record SolicitudPago(String referenciaInterna, BigDecimal monto, String ultimos4, String titular) {
    }

    record ResultadoPago(boolean aprobado, String referencia, String motivoRechazo) {

        public static ResultadoPago aprobado(String referencia) {
            return new ResultadoPago(true, referencia, null);
        }

        public static ResultadoPago rechazado(String motivo) {
            return new ResultadoPago(false, null, motivo);
        }
    }
}
