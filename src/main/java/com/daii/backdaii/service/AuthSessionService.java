package com.daii.backdaii.service;

import com.daii.backdaii.domain.entity.RefreshToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.RefreshTokenRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.AuthSession;
import com.daii.backdaii.exception.ApiException;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.security.JwtTokenProvider;
import com.daii.backdaii.security.OpaqueTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioMapper usuarioMapper;

    @Transactional
    public AuthSession create(String email, String userAgent) {
        Usuario user = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales inválidas"));
        return createFor(user, userAgent, UUID.randomUUID());
    }

    @Transactional
    public AuthSession rotate(String rawToken, String userAgent) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(opaqueTokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "La sesión no es válida"));
        if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Reuso o vencimiento: se cierran todas las sesiones del usuario.
            refreshTokenRepository.deleteByUsuarioId(current.getUsuario().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "La sesión expiró; iniciá sesión nuevamente");
        }
        current.setRevokedAt(LocalDateTime.now());
        AuthSession next = createFor(current.getUsuario(), userAgent, current.getFamilyId());
        current.setReplacedBy(refreshTokenRepository.findByTokenHash(opaqueTokenService.hash(next.refreshToken()))
                .orElseThrow().getId());
        return next;
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null) return;
        refreshTokenRepository.findByTokenHash(opaqueTokenService.hash(rawToken))
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    private AuthSession createFor(Usuario user, String userAgent, UUID familyId) {
        String raw = opaqueTokenService.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .id(UUID.randomUUID()).usuario(user).familyId(familyId)
                .tokenHash(opaqueTokenService.hash(raw)).userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(30)).build());
        return new AuthSession(new AuthResponse(jwtTokenProvider.generateToken(user), usuarioMapper.toDTO(user)), raw);
    }
}
