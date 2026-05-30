package com.focuscomex.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.PasswordResetToken;
import com.focuscomex.model.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findAllByUserAndUsedFalseAndExpiryDateAfter(User user, Date now);
}