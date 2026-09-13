package com.daii.backdaii.dto.medioPago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedioPagoRequest {

    @NotBlank(message = "El titular es obligatorio")
    private String titular;

    @NotBlank(message = "El número de tarjeta es obligatorio")
    @Size(min = 13, max = 19, message = "El número de tarjeta debe tener entre 13 y 19 dígitos")
    private String numeroTarjeta;

    @NotBlank(message = "El vencimiento es obligatorio")
    private String vencimiento;

    @NotBlank(message = "El código de seguridad es obligatorio")
    @Size(min = 3, max = 4, message = "El código de seguridad debe tener 3 o 4 dígitos")
    private String codigoSeguridad;
}