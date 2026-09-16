package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
}
