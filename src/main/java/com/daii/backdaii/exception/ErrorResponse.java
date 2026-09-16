package com.daii.backdaii.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Error body shared by every endpoint. The front reads {@code message};
 * {@code errors} maps each invalid field to its message and {@code code}
 * identifies cases the client handles specially.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, Map<String, String> errors, String code) {

    public ErrorResponse(String message) {
        this(message, null, null);
    }

    public ErrorResponse(String message, Map<String, String> errors) {
        this(message, errors, null);
    }
}
