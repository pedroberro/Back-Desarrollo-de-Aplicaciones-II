package com.daii.backdaii.mensajeria;

import com.daii.backdaii.service.impl.BarridoCobros;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the charge sweep every so often. Only with messaging on: without it nothing is queued. */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class ProgramadorBarridoCobros {

    private final BarridoCobros barrido;

    @Scheduled(initialDelayString = "${app.mensajeria.barrido.cada-ms:60000}",
            fixedDelayString = "${app.mensajeria.barrido.cada-ms:60000}")
    public void barrer() {
        barrido.barrer();
    }
}
