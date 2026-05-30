package com.focuscomex.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mp_preaprobaciones")
public class MercadoPagoPreapproval {

	@Id
    private String id;
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suscripcion", nullable = false)
    private Suscripcion suscripcion;
    
    // --- Identificación y Estado ---
    
    @Column(name = "mp_status", nullable = false)
    private String mpStatus;
    
    @Column(name = "mp_payer_id")
    private Long mpPayerId;

    @Column(name = "external_reference")
    private String externalReference; // Para rastrear tu ID interno
    
    // --- URLs y Fechas ---
    
    @Column(name = "init_point_url")
    private String initPointUrl; 
    
    @Column(name = "date_created", updatable = false)
    private OffsetDateTime dateCreated;

    @Column(name = "last_modified")
    private OffsetDateTime lastModified;
    
    @Column(name = "next_payment_date")
    private OffsetDateTime nextPaymentDate;
    
    @Column(name = "last_payment_date")
    private OffsetDateTime lastPaymentDate;
    
    // --- Detalles de Cobro (del auto_recurring) ---

    @Column(name = "transaction_amount")
    private BigDecimal transactionAmount;

    @Column(name = "currency_id")
    private String currencyId;
}
