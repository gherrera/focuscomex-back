package com.focuscomex.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeRequestDTO {

	@NotNull
    @Min(value = 100, message = "Monto mínimo es $100")
    @Max(value = 500000, message = "Monto máximo es $500.000")
    private Integer amountInPesos;
    
    @NotNull
    @Min(value = 1, message = "Mínimo 1 token")
    private Integer tokensExpected;

}
