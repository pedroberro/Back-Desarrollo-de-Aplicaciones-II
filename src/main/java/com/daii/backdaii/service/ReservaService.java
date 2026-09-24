package com.daii.backdaii.service;

import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.MisReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;

/** Customer side of the bookings component: book, list and cancel your own bookings. */
public interface ReservaService {

    /** Revalidates the slot against fresh data. The booking starts PENDIENTE. */
    ReservaDTO crear(String email, CrearReservaRequest request);

    MisReservas misReservas(String email);

    /** Own bookings only, unless the caller is an administrator. Includes the change log. */
    ReservaDTO detalle(String email, Long id, boolean esAdmin);

    ReservaDTO cancelar(String email, Long id);
}
