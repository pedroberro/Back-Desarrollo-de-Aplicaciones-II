package com.daii.backdaii.domain.evento;

/** The sweep requested again the charge of a booking that got no answer. */
public record CobroReencolado(Long reservaId, int intento) {
}
