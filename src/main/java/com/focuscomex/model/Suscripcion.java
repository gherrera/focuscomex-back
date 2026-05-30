package com.focuscomex.model;

import java.time.OffsetDateTime;

import com.focuscomex.enums.SuscripcionSource;
import com.focuscomex.enums.SuscripcionStatus;

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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "suscripciones")
public class Suscripcion {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private User user;
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plan", nullable = false)
    private Plan plan;
	
	@Column(name="started_at", nullable = false, updatable = false)
	private OffsetDateTime startedAt = OffsetDateTime.now();
	
	@Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;
    
    @Column(name = "resumed_at")
    private OffsetDateTime resumedAt;
    
    @Column(name = "paused_days_remaining")
    private Long pausedDaysRemaining;

    @Enumerated(EnumType.STRING)
	@Column(name="subscription_status", nullable = false)
    private SuscripcionStatus status;
    
    @Enumerated(EnumType.STRING)
	@Column(name="source", nullable = false)
    private SuscripcionSource source;
    
    @Column(name="is_paid", nullable = false)
    private boolean paid;
    
    @Column(name="external_provider")
    private String externalProvider;
    
    @Column(name="external_subscription_id")
    private String externalSubscriptionId;
    
    @Column(name = "cancel_at_period_end")
    private boolean cancelAtPeriodEnd;
    
    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;
    
    @Column(name = "granted_by")
    private String grantedBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mp_preapproval_id")
    private MercadoPagoPreapproval mpPreapprovalDetails;
}
