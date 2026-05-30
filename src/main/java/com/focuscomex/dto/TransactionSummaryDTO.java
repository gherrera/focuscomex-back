package com.focuscomex.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para mostrar el resumen de transacciones de la billetera
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDTO {
    private BigDecimal totalSpent;
    private Integer totalTokensPurchased;
    private Integer totalTokensConsumed;
    private Long totalTransactions;
}