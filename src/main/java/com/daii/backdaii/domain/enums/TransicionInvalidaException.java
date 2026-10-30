package com.daii.backdaii.domain.enums;

/** A booking was asked to move to a state its current state does not allow; answered with 409. */
public class TransicionInvalidaException extends RuntimeException {

    public static final String CODE = "TRANSICION_INVALIDA";

    private final EstadoReserva origen;
    private final EstadoReserva destino;

    public TransicionInvalidaException(EstadoReserva origen, EstadoReserva destino) {
        super("Una reserva " + origen.etiqueta() + " no puede pasar a " + destino.etiqueta());
        this.origen = origen;
        this.destino = destino;
    }

    public EstadoReserva getOrigen() {
        return origen;
    }

    public EstadoReserva getDestino() {
        return destino;
    }
}
