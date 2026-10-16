package com.daii.backdaii.security;

import com.daii.backdaii.domain.repository.RefreshTokenRepository;
import com.daii.backdaii.domain.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractJwtFromRequest(request);
        Optional<Claims> claims = jwtTokenProvider.leerAcceso(token);

        if (claims.isPresent() && !tokenBlacklistRepository.existsByToken(token) && sesionVigente(claims.get())) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(claims.get().getSubject());
                // Changing or resetting the password closes every session opened before it.
                boolean anterior = userDetails instanceof UsuarioPrincipal principal
                        && principal.tokenAnteriorAlCambio(JwtTokenProvider.emitido(claims.get()));
                if (!anterior && userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (UsernameNotFoundException e) {
                // The account no longer exists: the request goes on unauthenticated.
            }
        }

        filterChain.doFilter(request, response);
    }

    /** A revoked session family (logout, reused refresh token) also ends its access tokens. */
    private boolean sesionVigente(Claims claims) {
        return JwtTokenProvider.familia(claims)
                .map(familia -> !refreshTokenRepository.existsByFamiliaAndRevocadoElIsNotNull(familia))
                .orElse(false);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
