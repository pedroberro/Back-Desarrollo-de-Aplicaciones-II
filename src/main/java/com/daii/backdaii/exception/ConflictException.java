package com.daii.backdaii.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public ConflictException(String message, String code) {
        super(HttpStatus.CONFLICT, message, code);
    }
}
