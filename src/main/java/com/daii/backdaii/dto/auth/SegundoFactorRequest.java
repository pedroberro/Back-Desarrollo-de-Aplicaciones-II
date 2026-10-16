package com.daii.backdaii.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SegundoFactorRequest(
        @NotBlank(message = "Falta el desafío del inicio de sesión") String desafio,
        @NotBlank(message = "Ingresá el código de tu app de autenticación")
        @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos") String codigo) {
}
