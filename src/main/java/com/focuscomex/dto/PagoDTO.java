package com.focuscomex.dto;

import java.util.Date;

import com.focuscomex.enums.PaymentStatus;

import lombok.Data;

@Data
public class PagoDTO {
    private Long id;
    private Date createdAt;
    private Integer monto;
    private PaymentStatus status;
    private String medioPago;
}
