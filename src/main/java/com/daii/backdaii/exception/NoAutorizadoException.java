package com.daii.backdaii.exception;

import org.springframework.http.HttpStatus;

/** 401 with a user-facing message: an expired or reused session, or a wrong second-factor code. */
public class NoAutorizadoException extends ApiException {

    public NoAutorizadoException(String message, String code) {
        super(HttpStatus.UNAUTHORIZED, message, code);
    }
}
