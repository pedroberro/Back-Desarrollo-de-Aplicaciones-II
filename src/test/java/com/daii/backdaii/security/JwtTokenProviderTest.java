package com.daii.backdaii.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    @Test
    void createsAndValidatesAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider("development-secret-with-at-least-thirty-two-characters", 900_000);
        String token = provider.generateToken("ana@example.com", 42L, "CLIENTE");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmailFromToken(token)).isEqualTo("ana@example.com");
        assertThat(provider.extractExpiration(token)).isAfter(java.time.LocalDateTime.now());
    }
}
