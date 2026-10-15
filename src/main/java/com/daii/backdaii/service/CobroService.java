package com.daii.backdaii.service;

public interface CobroService {

    /**
     * Charges a PENDIENTE booking and leaves it CONFIRMADA if the payment went through, or
     * CANCELADA (which frees the slot) if it was declined. Idempotent: a booking that is no
     * longer pending, or that was already charged, is left as is.
     */
    void procesar(Long reservaId);
}
