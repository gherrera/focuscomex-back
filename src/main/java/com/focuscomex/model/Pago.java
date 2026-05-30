package com.focuscomex.model;

import java.util.Date;

import com.focuscomex.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pagos")
public class Pago {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Column(name="created_at")
    private Date createdAt;
	
	@Column(name="monto", nullable = false)
    private Integer monto;

    @Enumerated(EnumType.STRING)
	@Column(name="estado", nullable = false)
    private PaymentStatus status;
    
	@Column(name="medio_pago")
    private String medioPago;
}
