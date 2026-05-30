package com.focuscomex.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_payments",
       indexes = {
           @Index(name = "idx_sub_payment_subscription", columnList = "subscription_id"),
           @Index(name = "idx_sub_payment_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

	@Id
    @Column(name = "id", length = 64)
    private String id;
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", referencedColumnName = "id")
    private MercadoPagoPreapproval subscription;
	
	 @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "status_detail", length = 255)
    private String statusDetail;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
	
    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) this.createdAt = OffsetDateTime.now();
    }
}
