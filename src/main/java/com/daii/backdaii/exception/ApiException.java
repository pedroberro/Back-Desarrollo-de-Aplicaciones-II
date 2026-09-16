package com.daii.backdaii.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for business errors. The handler turns each subclass into its HTTP status
 * and exposes the message to the client, so messages must be user-facing.
 * {@code code} is optional and lets the client react to a specific case.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    protected ApiException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
