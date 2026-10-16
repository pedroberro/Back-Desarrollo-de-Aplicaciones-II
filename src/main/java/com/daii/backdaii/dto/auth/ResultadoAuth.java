package com.daii.backdaii.dto.auth;

/** What a sign-in step returns to the controller: the body, and the refresh token for the cookie (null when none). */
public record ResultadoAuth(AuthResponse respuesta, String refresh) {
}
