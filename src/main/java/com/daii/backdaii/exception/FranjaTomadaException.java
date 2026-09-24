package com.daii.backdaii.exception;

import com.daii.backdaii.dto.catalogo.CatalogoDTOs.FranjaDTO;
import lombok.Getter;

import java.util.List;

/**
 * The slot is no longer free, usually because someone else booked it while this customer was
 * confirming. Carries the nearest free ranges so the client can offer them without another call.
 */
@Getter
public class FranjaTomadaException extends ConflictException {

    public static final String CODE = "FRANJA_OCUPADA";

    private final List<FranjaDTO> alternativas;

    public FranjaTomadaException(String message, List<FranjaDTO> alternativas) {
        super(message, CODE);
        this.alternativas = alternativas;
    }
}
