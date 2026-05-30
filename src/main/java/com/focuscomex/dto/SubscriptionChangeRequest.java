package com.focuscomex.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionChangeRequest {
    private Long newPlanId;
    private String changeType; // "immediate" o "next_period"
}