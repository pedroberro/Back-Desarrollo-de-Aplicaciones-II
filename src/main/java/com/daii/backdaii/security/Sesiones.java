package com.daii.backdaii.security;

import com.daii.backdaii.domain.entity.RefreshToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.RefreshTokenRepository;
import com.daii.backdaii.exception.NoAutorizadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Session families and their rotating refresh tokens (SEC-02). Every sign-in opens a family;
 * every refresh spends the presented token and issues the next one. A spent token presented
 * again means someone kept a copy: the family is revoked and both holders are signed out.
 */
@Component
@RequiredArgsConstructor
public class Sesiones {

    public static final String CODIGO_REUTILIZADO = "REFRESH_REUTILIZADO";

    private final RefreshTokenRepository repositorio;
    private final Clock reloj;

    @Value("${app.jwt.refresh-dias:7}")
    private long refreshDias;

    /** A new family, for a sign-in. */
    public Emitido abrir(Usuario usuario) {
        return emitir(usuario, UUID.randomUUID());
    }

    /**
     * Spends the token and issues the next one of its family. Runs in its own transaction so a
     * detected reuse stays revoked even though the request then fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = NoAutorizadoException.class)
    public Emitido rotar(String crudo) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        RefreshToken actual = repositorio.findParaRotarByTokenHash(SecureTokens.hash(crudo == null ? "" : crudo))
                .orElseThrow(() -> new NoAutorizadoException("La sesión expiró. Ingresá de nuevo.", "SESION_INVALIDA"));
        if (actual.getRevocadoEl() != null || !actual.getExpiraEl().isAfter(ahora)) {
            throw new NoAutorizadoException("La sesión expiró. Ingresá de nuevo.", "SESION_INVALIDA");
        }
        if (actual.getUsadoEl() != null) {
            repositorio.revocarFamilia(actual.getFamilia(), ahora);
            throw new NoAutorizadoException("Por seguridad cerramos la sesión: ingresá de nuevo.", CODIGO_REUTILIZADO);
        }
        actual.setUsadoEl(ahora);
        return emitir(actual.getUsuario(), actual.getFamilia());
    }

    /** Owner of a refresh token, valid or not: for auditing a failed refresh. */
    @Transactional(readOnly = true)
    public String duenio(String crudo) {
        return crudo == null ? null : repositorio.findByTokenHash(SecureTokens.hash(crudo))
                .map(t -> t.getUsuario().getEmail()).orElse(null);
    }

    @Transactional
    public void cerrar(String crudo) {
        if (crudo != null) {
            repositorio.findByTokenHash(SecureTokens.hash(crudo))
                    .ifPresent(t -> repositorio.revocarFamilia(t.getFamilia(), LocalDateTime.now(reloj)));
        }
    }

    /** Every session of the user: after a password change or reset. */
    @Transactional
    public void cerrarTodas(Usuario usuario) {
        repositorio.revocarDelUsuario(usuario.getId(), LocalDateTime.now(reloj));
    }

    public Duration duracion() {
        return Duration.ofDays(refreshDias);
    }

    private Emitido emitir(Usuario usuario, UUID familia) {
        String crudo = SecureTokens.generar();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        repositorio.save(RefreshToken.builder()
                .usuario(usuario)
                .familia(familia)
                .tokenHash(SecureTokens.hash(crudo))
                .creadoEl(ahora)
                .expiraEl(ahora.plus(duracion()))
                .build());
        return new Emitido(familia, crudo, usuario);
    }

    /** {@code crudo} goes to the client in an HttpOnly cookie and is never stored. */
    public record Emitido(UUID familia, String crudo, Usuario usuario) {
    }
}
