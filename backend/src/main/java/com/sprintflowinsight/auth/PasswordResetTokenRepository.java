package com.sprintflowinsight.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByUser(User user);
}
