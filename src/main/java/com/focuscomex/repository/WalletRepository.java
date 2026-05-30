package com.focuscomex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.model.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Wallet w SET w.tokens = w.tokens + :tokens, w.updatedAt = CURRENT_TIMESTAMP WHERE w.id = :walletId")
    int addTokens(@Param("walletId") Long walletId, @Param("tokens") Integer tokens);
    
    @Modifying
    @Transactional
    @Query("UPDATE Wallet w SET w.tokens = w.tokens - :tokens, w.updatedAt = CURRENT_TIMESTAMP WHERE w.id = :walletId AND w.tokens >= :tokens")
    int consumeTokens(@Param("walletId") Long walletId, @Param("tokens") Integer tokens);

}