package com.daii.backdaii.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Same contract in memory, for tests: one instance, lost on restart. */
public class AlmacenIntentosEnMemoria implements AlmacenIntentos {

    private record Ventana(long inicio, int cantidad) {
    }

    private final Clock reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public AlmacenIntentosEnMemoria(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public int sumar(String clave, Duration ventana) {
        long ahora = reloj.millis();
        return ventanas.compute(clave, (k, v) -> v == null || ahora - v.inicio() >= ventana.toMillis()
                ? new Ventana(ahora, 1) : new Ventana(v.inicio(), v.cantidad() + 1)).cantidad();
    }

    @Override
    public int contar(String clave, Duration ventana) {
        Ventana v = ventanas.get(clave);
        return v == null || reloj.millis() - v.inicio() >= ventana.toMillis() ? 0 : v.cantidad();
    }

    @Override
    public void borrar(String clave) {
        ventanas.remove(clave);
    }

    @Override
    public int descartarVencidos(Duration antiguedad) {
        long ahora = reloj.millis();
        int antes = ventanas.size();
        ventanas.values().removeIf(v -> ahora - v.inicio() >= antiguedad.toMillis());
        return antes - ventanas.size();
    }
}
