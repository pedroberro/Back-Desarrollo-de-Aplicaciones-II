package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.EmailConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, Long> {

    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    void deleteByUsuarioIdAndUsedAtIsNull(Long usuarioId);
}
