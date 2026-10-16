package com.daii.backdaii.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and reads the two kinds of JWT the API issues, told apart by the {@code typ} claim:
 * short-lived access tokens (SEC-02), which carry the session family in {@code sid}, and the
 * five-minute challenge between the password and the second factor (SEC-03). A challenge is
 * never accepted as an access token.
 */
@Component
public class JwtTokenProvider {

    static final String TIPO_ACCESO = "acceso";
    static final String TIPO_DESAFIO = "segundo-factor";
    private static final long DESAFIO_MS = 5 * 60 * 1000;

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Secretos.requerido("JWT_SECRET", secret).getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, Long id, String rol, UUID familia) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("typ", TIPO_ACCESO)
                .claim("sid", familia.toString())
                .claim("id", id)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String generarDesafio(String email) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("typ", TIPO_DESAFIO)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + DESAFIO_MS))
                .signWith(key)
                .compact();
    }

    /** Claims of a valid access token; empty for anything else (expired, forged, a challenge). */
    public Optional<Claims> leerAcceso(String token) {
        return leer(token).filter(c -> TIPO_ACCESO.equals(c.get("typ", String.class)));
    }

    /** Email of a valid second-factor challenge. */
    public Optional<String> leerDesafio(String token) {
        return leer(token).filter(c -> TIPO_DESAFIO.equals(c.get("typ", String.class))).map(Claims::getSubject);
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public static Optional<UUID> familia(Claims claims) {
        String sid = claims.get("sid", String.class);
        return sid == null ? Optional.empty() : Optional.of(UUID.fromString(sid));
    }

    public static Instant emitido(Claims claims) {
        return claims.getIssuedAt().toInstant();
    }

    public static LocalDateTime vence(Claims claims) {
        return claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Optional<Claims> leer(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
