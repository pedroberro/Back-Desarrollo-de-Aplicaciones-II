package com.daii.backdaii.service;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;

import java.util.List;

public interface MedioPagoService {

    MedioPagoResumen agregarTarjeta(String email, MedioPagoRequest request);

    List<MedioPagoResumen> listar(String email);

    MedioPagoResumen actualizar(String email, Long id, MedioPagoUpdateRequest request);

    /** Makes the card the user's primary one and returns the updated list. */
    List<MedioPagoResumen> marcarPrincipal(String email, Long id);

    void eliminar(String email, Long id);
}