package com.focuscomex.model;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.focuscomex.enums.TransactionStatus;
import com.focuscomex.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;
    
    @Column(name = "amount_in_pesos", precision = 10, scale = 2)
    private Integer amountInPesos;
    
    @Column(name = "tokens")
    private Integer tokens;
    
    @Column(name = "token_price_at_time", precision = 10, scale = 2)
    private Integer tokenPriceAtTime;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "external_payment_id")
    private String externalPaymentId; // ID de MercadoPago
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
