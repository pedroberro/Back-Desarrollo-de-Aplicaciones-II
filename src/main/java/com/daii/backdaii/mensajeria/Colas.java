package com.daii.backdaii.mensajeria;

/** Names of the broker objects. Kept in one place so the producer and the consumer cannot drift. */
public final class Colas {

    /** Direct exchange of the bookings domain: each routing key reaches exactly one queue. */
    public static final String EXCHANGE_RESERVAS = "planta-libre.reservas";
    public static final String COBRO_SOLICITADO = "cobro.solicitado";

    /** Point-to-point queue: every charge request is processed once, by one consumer. */
    public static final String COBROS = "planta-libre.cobros";

    /** Where a request lands after its retries run out, to be inspected by hand. */
    public static final String EXCHANGE_DLX = "planta-libre.dlx";
    public static final String COBROS_DLQ = "planta-libre.cobros.dlq";

    private Colas() {
    }
}
