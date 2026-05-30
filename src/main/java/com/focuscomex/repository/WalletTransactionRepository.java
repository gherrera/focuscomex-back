package com.focuscomex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.focuscomex.enums.TransactionType;
import com.focuscomex.model.WalletTransaction;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    
	@Query("SELECT t FROM WalletTransaction t WHERE t.wallet.user.id = :userId ORDER BY t.createdAt DESC")
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.user.id = :userId AND t.type = :type ORDER BY t.createdAt DESC")
    Page<WalletTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(
        @Param("userId") Long userId, 
        @Param("type") TransactionType type, 
        Pageable pageable
    );
    
    Optional<WalletTransaction> findByExternalPaymentId(String externalPaymentId);
    
    /**
     * Obtiene resumen de transacciones agrupado por tipo
     */
    @Query("SELECT t.type, SUM(t.amountInPesos), SUM(t.tokens), COUNT(t) " +
           "FROM WalletTransaction t " +
           "WHERE t.wallet.user.id = :userId AND t.status = 'COMPLETED' " +
           "GROUP BY t.type")
    List<Object[]> getTransactionSummaryByUserId(@Param("userId") Long userId);

}