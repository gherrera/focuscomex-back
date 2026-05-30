package com.focuscomex.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "wallets")
public class Wallet {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;
	
	@Column(name = "tokens", nullable = false)
    private Integer tokens = 0;
	
	@Column(name = "created_at")
    private OffsetDateTime createdAt;
	
	@Column(name = "updated_at")
    private OffsetDateTime updatedAt;
	
	public Wallet(User user) {
        this.user = user;
        this.tokens = 0;
        this.createdAt = OffsetDateTime.now();
    }
}
