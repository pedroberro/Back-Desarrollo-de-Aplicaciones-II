package com.daii.backdaii.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for business errors. The handler turns each subclass into its HTTP status
 * and exposes the message to the client, so messages must be user-facing.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
