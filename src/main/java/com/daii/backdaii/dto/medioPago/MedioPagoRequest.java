package com.daii.backdaii.dto.medioPago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedioPagoRequest {

    @NotBlank(message = "El titular es obligatorio")
    @Size(max = 100, message = "El titular no puede superar los 100 caracteres")
    private String titular;

    // 13 to 19 digits, optionally grouped with single spaces.
    @NotBlank(message = "El número de tarjeta es obligatorio")
    @Pattern(regexp = "^(?:\\d ?){12,18}\\d$", message = "El número de tarjeta debe tener entre 13 y 19 dígitos")
    private String numeroTarjeta;

    @NotBlank(message = "El vencimiento es obligatorio")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "El vencimiento debe tener el formato MM/AA")
    private String vencimiento;

    @NotBlank(message = "El código de seguridad es obligatorio")
    @Pattern(regexp = "^\\d{3,4}$", message = "El código de seguridad debe tener 3 o 4 dígitos")
    private String codigoSeguridad;
}
