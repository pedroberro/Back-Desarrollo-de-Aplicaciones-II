package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Locked: two refreshes with the same token run one after the other, and the second sees it spent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.usuario where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findParaRotarByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean existsByFamiliaAndRevocadoElIsNotNull(UUID familia);

    @Modifying
    @Query("update RefreshToken t set t.revocadoEl = :ahora where t.familia = :familia and t.revocadoEl is null")
    int revocarFamilia(UUID familia, LocalDateTime ahora);

    @Modifying
    @Query("update RefreshToken t set t.revocadoEl = :ahora where t.usuario.id = :usuarioId and t.revocadoEl is null")
    int revocarDelUsuario(Long usuarioId, LocalDateTime ahora);
}
