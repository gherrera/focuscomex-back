package com.focuscomex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequest {
    @NotEmpty(message = "El email es requerido")
    @Email(message = "El email no es válido")
    private String email;
}