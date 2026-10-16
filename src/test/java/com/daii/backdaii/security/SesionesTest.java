package com.daii.backdaii.security;

import com.daii.backdaii.domain.entity.RefreshToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.RefreshTokenRepository;
import com.daii.backdaii.exception.NoAutorizadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SEC-02: rotation of the refresh token and revocation of the family when a spent one comes back. */
@ExtendWith(MockitoExtension.class)
class SesionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-16T12:00:00Z");

    @Mock
    private RefreshTokenRepository repositorio;

    private Sesiones sesiones;
    private final Usuario ana = Usuario.builder().id(3L).email("ana@example.com").build();
    private final UUID familia = UUID.randomUUID();

    @BeforeEach
    void crear() {
        sesiones = new Sesiones(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
        ReflectionTestUtils.setField(sesiones, "refreshDias", 7L);
    }

    private RefreshToken guardado(String crudo, LocalDateTime usado) {
        RefreshToken token = RefreshToken.builder().id(1L).usuario(ana).familia(familia)
                .tokenHash(SecureTokens.hash(crudo)).creadoEl(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC))
                .expiraEl(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusDays(7)).usadoEl(usado).build();
        when(repositorio.findParaRotarByTokenHash(SecureTokens.hash(crudo))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void rotarGastaElTokenYEmiteOtroDeLaMismaFamilia() {
        RefreshToken actual = guardado("r1", null);

        Sesiones.Emitido nuevo = sesiones.rotar("r1");

        assertThat(actual.getUsadoEl()).isNotNull();
        assertThat(nuevo.familia()).isEqualTo(familia);
        assertThat(nuevo.crudo()).isNotEqualTo("r1");
        verify(repositorio).save(any(RefreshToken.class));
        verify(repositorio, never()).revocarFamilia(any(), any());
    }

    @Test
    void reutilizarUnTokenGastadoRevocaTodaLaFamilia() {
        guardado("r1", LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC).minusMinutes(5));

        assertThatThrownBy(() -> sesiones.rotar("r1"))
                .isInstanceOf(NoAutorizadoException.class)
                .extracting("code").isEqualTo(Sesiones.CODIGO_REUTILIZADO);
        verify(repositorio).revocarFamilia(any(), any());
        verify(repositorio, never()).save(any());
    }

    @Test
    void unTokenDesconocidoEs401() {
        assertThatThrownBy(() -> sesiones.rotar("inventado")).isInstanceOf(NoAutorizadoException.class);
    }
}
