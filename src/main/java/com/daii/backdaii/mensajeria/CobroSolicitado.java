package com.daii.backdaii.mensajeria;

import java.time.Instant;

/**
 * Message of the charge queue. It carries only the booking id: the consumer reads the current
 * state from the database, so a message that arrives late never acts on stale data.
 */
public record CobroSolicitado(Long reservaId, Instant solicitadoEl) {
}
