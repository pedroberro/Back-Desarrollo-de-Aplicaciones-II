package com.daii.backdaii.dto.medioPago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Only the holder and the expiry can change: the full card number is never stored,
 * so a different number means a new card.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedioPagoUpdateRequest {

    @NotBlank(message = "El titular es obligatorio")
    @Size(max = 100, message = "El titular no puede superar los 100 caracteres")
    private String titular;

    @NotBlank(message = "El vencimiento es obligatorio")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "El vencimiento debe tener el formato MM/AA")
    private String vencimiento;
}
