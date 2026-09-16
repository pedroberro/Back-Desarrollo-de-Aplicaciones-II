package com.daii.backdaii.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmarEmailRequest {

    @NotBlank(message = "El link de confirmación no es válido")
    private String token;
}
