package com.daii.backdaii.service;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;

import java.util.List;

public interface MedioPagoService {

    MedioPagoResumen agregarTarjeta(String email, MedioPagoRequest request);

    List<MedioPagoResumen> listar(String email);

    MedioPagoResumen actualizar(String email, Long id, MedioPagoUpdateRequest request);

    void eliminar(String email, Long id);
}