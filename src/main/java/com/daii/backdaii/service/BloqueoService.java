package com.daii.backdaii.service;

import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueoDTO;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueosCreados;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearBloqueoRequest;

import java.util.List;

/** Blocks take spaces out of circulation (maintenance, cleaning, events). Administrators only. */
public interface BloqueoService {

    /** "Piso entero" creates one block per bookable space of the floor, all or none. */
    BloqueosCreados crear(String adminEmail, CrearBloqueoRequest request);

    /** Current and upcoming blocks. */
    List<BloqueoDTO> listarVigentes();

    /** Removes one block, or every block created with it when {@code todoElGrupo}. */
    void eliminar(Long id, boolean todoElGrupo);
}
