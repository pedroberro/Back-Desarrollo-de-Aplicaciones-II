package com.daii.backdaii.config;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.enums.TipoEspacio;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.Map;

@Configuration
public class CobroConfig {

    /** Hourly rates per space kind, from app.tarifas.por-hora. Fails on startup if one is missing. */
    @Bean
    public Tarifario tarifario(Environment env) {
        Map<TipoEspacio, BigDecimal> porHora = Binder.get(env)
                .bind("app.tarifas.por-hora", Bindable.mapOf(TipoEspacio.class, BigDecimal.class))
                .orElse(Map.of());
        return new Tarifario(porHora);
    }
}
