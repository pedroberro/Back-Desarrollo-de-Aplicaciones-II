package com.daii.backdaii.security;

import java.time.Duration;

/** Where the attempt counters live. A fixed window per key: it starts with the first attempt. */
public interface AlmacenIntentos {

    /** Counts one attempt and returns how many there are in the current window, this one included. */
    int sumar(String clave, Duration ventana);

    /** Attempts in the current window, without counting one more; 0 when the window expired. */
    int contar(String clave, Duration ventana);

    void borrar(String clave);

    /** Forgets windows older than {@code antiguedad}; returns how many. */
    int descartarVencidos(Duration antiguedad);
}
