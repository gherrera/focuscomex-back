package com.focuscomex.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPreviewDTO {
    private String changeType; // "upgrade" o "downgrade"
    private String currentPlanName;
    private String newPlanName;
    private Integer currentPrice;
    private Integer newPrice;
    private BigDecimal proratedAmount; // Para upgrades inmediatos
    private LocalDateTime changeEffectiveDate;
    private String description;
    private Boolean canChange;
    private String reason; // Por qué no puede cambiar (si canChange = false)
}