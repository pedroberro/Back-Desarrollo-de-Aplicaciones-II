package com.daii.backdaii.security;

import com.daii.backdaii.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects passwords that appear in known breaches (SEC-04), on top of the format rule of the DTOs.
 * First an offline list of the most common ones, then the external breach corpus.
 */
@Component
public class PoliticaContrasenias {

    public static final String CODIGO = "PASSWORD_FILTRADA";

    private final Set<String> comunes;
    private final ConsultaFiltraciones consulta;
    private final boolean consultarExterno;

    public PoliticaContrasenias(ConsultaFiltraciones consulta,
                                @Value("${app.seguridad.filtraciones.consultar-externo:true}") boolean consultarExterno) {
        this.consulta = consulta;
        this.consultarExterno = consultarExterno;
        this.comunes = leerComunes();
    }

    public void validar(String password) {
        if (comunes.contains(password.toLowerCase(Locale.ROOT)) || (consultarExterno && consulta.filtrada(password))) {
            throw new BadRequestException("Esa contraseña aparece en filtraciones de datos conocidas. Elegí otra.", CODIGO);
        }
    }

    private static Set<String> leerComunes() {
        try {
            String texto = new ClassPathResource("seguridad/contrasenias-comunes.txt").getContentAsString(StandardCharsets.UTF_8);
            return texto.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
