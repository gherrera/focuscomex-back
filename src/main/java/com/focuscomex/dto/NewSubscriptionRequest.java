package com.focuscomex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewSubscriptionRequest {
    private Long planId;
    private String paymentMethodId; // Opcional para payment methods guardados
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
}