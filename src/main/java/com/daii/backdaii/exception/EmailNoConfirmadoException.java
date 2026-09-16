package com.daii.backdaii.exception;

import org.springframework.http.HttpStatus;

public class EmailNoConfirmadoException extends ApiException {

    public static final String CODE = "EMAIL_NO_CONFIRMADO";

    public EmailNoConfirmadoException() {
        super(HttpStatus.FORBIDDEN, "Confirmá tu email para ingresar. Te enviamos un link cuando te registraste.", CODE);
    }
}
