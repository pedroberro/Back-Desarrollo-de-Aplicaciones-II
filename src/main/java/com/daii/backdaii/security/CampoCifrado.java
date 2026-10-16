package com.daii.backdaii.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** JPA converter: the entity holds plaintext, the column holds {@link CifradoCampos} ciphertext. */
@Component
@Converter
public class CampoCifrado implements AttributeConverter<String, String> {

    private final CifradoCampos cifrado;

    public CampoCifrado(CifradoCampos cifrado) {
        this.cifrado = cifrado;
    }

    @Override
    public String convertToDatabaseColumn(String texto) {
        return cifrado.cifrar(texto);
    }

    @Override
    public String convertToEntityAttribute(String guardado) {
        return cifrado.descifrar(guardado);
    }
}
