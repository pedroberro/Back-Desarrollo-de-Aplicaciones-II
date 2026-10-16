package com.daii.backdaii.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Caps how often a client (an IP, an account, a signed-in user) may do something. The counters
 * live in an {@link AlmacenIntentos}: the database in production, so the limits are shared by every
 * instance and survive restarts (SEC-04, SEC-06).
 */
@Component
public class LimitadorDeIntentos {

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeIntentos.class);

    private final AlmacenIntentos almacen;
    private final Duration ventanaMasLarga = Arrays.stream(LimiteIntentos.values())
            .map(LimiteIntentos::getVentana).max(Duration::compareTo).orElse(Duration.ofHours(1));
    private ScheduledExecutorService limpieza;

    public LimitadorDeIntentos(AlmacenIntentos almacen) {
        this.almacen = almacen;
    }

    @PostConstruct
    void iniciar() {
        limpieza = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "limitador-intentos-limpieza");
            hilo.setDaemon(true);
            return hilo;
        });
        limpieza.scheduleAtFixedRate(this::descartarVencidas, 1, 5, TimeUnit.MINUTES);
        log.info("[ciclo de vida] LimitadorDeIntentos iniciado: {} acciones limitadas, contadores en {}",
                LimiteIntentos.values().length, almacen.getClass().getSimpleName());
    }

    @PreDestroy
    void detener() {
        limpieza.shutdownNow();
    }

    /** Counts the call and returns false once the client went over the limit in the current window. */
    public boolean permitir(LimiteIntentos limite, String cliente) {
        return intentar(limite, cliente) <= limite.getMaximo();
    }

    /** Counts the call and returns the attempts in the window, this one included. */
    public int intentar(LimiteIntentos limite, String cliente) {
        return almacen.sumar(clave(limite, cliente), limite.getVentana());
    }

    /** Whether the client already used up the window, without counting this call. */
    public boolean agotado(LimiteIntentos limite, String cliente) {
        return almacen.contar(clave(limite, cliente), limite.getVentana()) >= limite.getMaximo();
    }

    /** Forgets the client's count, e.g. after a successful sign-in. */
    public void reiniciar(LimiteIntentos limite, String cliente) {
        almacen.borrar(clave(limite, cliente));
    }

    void descartarVencidas() {
        try {
            int descartadas = almacen.descartarVencidos(ventanaMasLarga);
            if (descartadas > 0) {
                log.debug("LimitadorDeIntentos: {} contadores vencidos descartados", descartadas);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron descartar los contadores vencidos: {}", e.getMessage());
        }
    }

    private static String clave(LimiteIntentos limite, String cliente) {
        return limite.name() + "|" + cliente;
    }
}
