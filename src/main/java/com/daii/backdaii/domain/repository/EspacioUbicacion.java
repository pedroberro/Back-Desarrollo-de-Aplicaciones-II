package com.daii.backdaii.domain.repository;

/** Where a space hangs in the hierarchy. Lets the listings count free spaces without loading entities. */
public record EspacioUbicacion(Long espacioId, Long pisoId, Long edificioId, Long empresaId) {
}
