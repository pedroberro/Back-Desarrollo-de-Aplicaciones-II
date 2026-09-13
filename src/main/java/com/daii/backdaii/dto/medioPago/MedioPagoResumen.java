package com.daii.backdaii.dto.medioPago;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedioPagoResumen {

    private Long id;
    private String tipo;
    private String descripcion;
    private boolean verificado;
    private String estado;
}