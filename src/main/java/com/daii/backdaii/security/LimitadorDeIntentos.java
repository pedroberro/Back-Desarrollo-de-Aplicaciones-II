package com.daii.backdaii.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stateful component: remembers, between requests, how many times each client called each
 * limited action ({@link LimiteIntentos}) inside a fixed time window.
 *
 * <p>The Spring container owns its whole life cycle. It creates a single instance
 * (explicit singleton scope, so all requests share the counters), calls {@link #iniciar()}
 * once the dependencies are ready to start the periodic cleanup, and calls
 * {@link #detener()} on shutdown to stop that thread and drop the state.
 *
 * <p>The counters live in memory: they reset on restart and are not shared between
 * instances, which is enough for a single backend.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class LimitadorDeIntentos {

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeIntentos.class);

    private record Ventana(LimiteIntentos limite, long inicioMillis, int cantidad) {

        boolean vencida(long ahora) {
            return ahora - inicioMillis >= limite.getVentana().toMillis();
        }
    }

    private final Clock clock;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private ScheduledExecutorService limpieza;

    public LimitadorDeIntentos() {
        this(Clock.systemUTC());
    }

    LimitadorDeIntentos(Clock clock) {
        this.clock = clock;
    }

    @PostConstruct
    void iniciar() {
        limpieza = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "limitador-intentos-limpieza");
            hilo.setDaemon(true);
            return hilo;
        });
        limpieza.scheduleAtFixedRate(this::descartarVencidas, 1, 1, TimeUnit.MINUTES);
        log.info("[ciclo de vida] LimitadorDeIntentos iniciado: {} acciones limitadas, limpieza cada 1 minuto",
                LimiteIntentos.values().length);
    }

    @PreDestroy
    void detener() {
        limpieza.shutdownNow();
        log.info("[ciclo de vida] LimitadorDeIntentos detenido: se descartan {} contadores en memoria", ventanas.size());
        ventanas.clear();
    }

    /** Counts the call and returns false once the client went over the limit in the current window. */
    public boolean permitir(LimiteIntentos limite, String cliente) {
        long ahora = clock.millis();
        Ventana ventana = ventanas.compute(limite.name() + "|" + cliente, (clave, actual) ->
                actual == null || actual.vencida(ahora)
                        ? new Ventana(limite, ahora, 1)
                        : new Ventana(limite, actual.inicioMillis(), actual.cantidad() + 1));
        return ventana.cantidad() <= limite.getMaximo();
    }

    /** Whether the client already used up the window, without counting this call. */
    public boolean agotado(LimiteIntentos limite, String cliente) {
        Ventana ventana = ventanas.get(limite.name() + "|" + cliente);
        return ventana != null && !ventana.vencida(clock.millis()) && ventana.cantidad() >= limite.getMaximo();
    }

    /** Forgets the client's count, e.g. after a successful sign-in. */
    public void reiniciar(LimiteIntentos limite, String cliente) {
        ventanas.remove(limite.name() + "|" + cliente);
    }

    void descartarVencidas() {
        long ahora = clock.millis();
        int antes = ventanas.size();
        ventanas.values().removeIf(v -> v.vencida(ahora));
        int descartadas = antes - ventanas.size();
        if (descartadas > 0) {
            log.debug("LimitadorDeIntentos: {} contadores vencidos descartados", descartadas);
        }
    }
}
