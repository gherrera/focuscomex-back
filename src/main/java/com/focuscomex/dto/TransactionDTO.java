package com.focuscomex.dto;

import java.time.OffsetDateTime;

import com.focuscomex.enums.TransactionStatus;
import com.focuscomex.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {

	private Long id;
    private TransactionType type;
    private Integer amountInPesos;
    private Integer tokens;
    private Integer tokenPriceAtTime;
    private String description;
    private TransactionStatus status;
    private OffsetDateTime createdAt;
    private String externalPaymentId; // ID de MercadoPago

}
