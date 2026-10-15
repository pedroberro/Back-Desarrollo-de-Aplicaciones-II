package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudPago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasarelaPagosSimuladaTest {

    private final PasarelaPagosSimulada pasarela = new PasarelaPagosSimulada(0);

    private static SolicitudPago solicitud(String ultimos4) {
        return new SolicitudPago("R-1", new BigDecimal("7000.00"), ultimos4, "Ana Pérez");
    }

    @Test
    void apruebaUnaTarjetaComunConReferencia() {
        ResultadoPago resultado = pasarela.cobrar(solicitud("4242"));

        assertThat(resultado.aprobado()).isTrue();
        assertThat(resultado.referencia()).startsWith("SIM-");
        assertThat(resultado.motivoRechazo()).isNull();
    }

    @Test
    void rechazaLaTarjetaDePruebaTerminadaEn0002() {
        ResultadoPago resultado = pasarela.cobrar(solicitud(PasarelaPagosSimulada.TERMINACION_RECHAZADA));

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.referencia()).isNull();
        assertThat(resultado.motivoRechazo()).isNotBlank();
    }

    @Test
    void laTarjetaTerminadaEn0119SimulaUnaFallaTecnica() {
        assertThatThrownBy(() -> pasarela.cobrar(solicitud(PasarelaPagosSimulada.TERMINACION_FALLA_TECNICA)))
                .isInstanceOf(IllegalStateException.class);
    }
}
