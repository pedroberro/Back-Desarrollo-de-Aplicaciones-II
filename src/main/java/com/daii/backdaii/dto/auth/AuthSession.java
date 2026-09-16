package com.daii.backdaii.dto.auth;

/** Resultado interno: el refresh token sólo se entrega mediante cookie HttpOnly. */
public record AuthSession(AuthResponse response, String refreshToken) { }
