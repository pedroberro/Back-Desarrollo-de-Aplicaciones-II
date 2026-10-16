package com.daii.backdaii.security;

/** Asks an external breach corpus whether a password was leaked. */
public interface ConsultaFiltraciones {

    /** True if it appears in known breaches. Implementations never send the password itself. */
    boolean filtrada(String password);
}
