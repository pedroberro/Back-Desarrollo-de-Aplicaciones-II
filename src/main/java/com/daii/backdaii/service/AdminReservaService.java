package com.daii.backdaii.service;

import com.daii.backdaii.dto.reserva.ReservaDTOs.AdminReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AgendaPiso;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ClienteBusqueda;
import com.daii.backdaii.dto.reserva.ReservaDTOs.FiltroReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.PaginaReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Administrator side of the bookings component. An administrator may overrule a customer's
 * booking, but never silently: every edit needs a reason, lands in the booking's change log
 * and is mailed to the customer.
 */
public interface AdminReservaService {

    PaginaReservas listar(FiltroReservas filtro, Long espacioId, int pagina, int tamanio);

    AgendaPiso agenda(Long pisoId, LocalDate fecha);

    /** Books on behalf of a customer. Starts PENDIENTE, like a customer's own booking. */
    ReservaDTO crear(String adminEmail, AdminReservaRequest request);

    ReservaDTO actualizar(String adminEmail, Long id, AdminReservaRequest request);

    void eliminar(String adminEmail, Long id);

    List<ClienteBusqueda> buscarClientes(String texto);
}
