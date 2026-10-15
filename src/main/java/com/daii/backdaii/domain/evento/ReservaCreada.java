package com.daii.backdaii.domain.evento;

/**
 * Published inside the transaction that creates a booking. Listeners that talk to the outside
 * world react after commit, so a booking that rolled back never leaves the app.
 */
public record ReservaCreada(Long reservaId) {
}
