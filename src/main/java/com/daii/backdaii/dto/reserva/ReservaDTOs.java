package com.daii.backdaii.dto.reserva;

import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.MotivoBloqueo;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EdificioRef;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EspacioBreve;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.FranjaDTO;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.Ref;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Requests and responses of the bookings component, for customers and administrators. */
public final class ReservaDTOs {

    private ReservaDTOs() {
    }

    public record CrearReservaRequest(
            @NotNull(message = "Elegí un espacio") Long espacioId,
            @NotNull(message = "Elegí el horario de inicio") LocalDateTime inicio,
            @NotNull(message = "Elegí el horario de fin") LocalDateTime fin,
            /* Card to charge; null charges the customer's main card. */
            Long medioPagoId) {
    }

    /**
     * Create and edit from the admin form. {@code estado} and {@code motivo} only apply when
     * editing; the reason is mandatory when the booking belongs to someone else.
     */
    public record AdminReservaRequest(
            @NotNull(message = "Elegí para quién es la reserva") Long usuarioId,
            @NotNull(message = "Elegí un espacio") Long espacioId,
            @NotNull(message = "Elegí el horario de inicio") LocalDateTime inicio,
            @NotNull(message = "Elegí el horario de fin") LocalDateTime fin,
            EstadoReserva estado,
            @Size(max = 500, message = "El motivo no puede superar los 500 caracteres") String motivo) {
    }

    public record MotivoRequest(
            @Size(max = 500, message = "El motivo no puede superar los 500 caracteres") String motivo) {
    }

    public record EspacioRef(Long id, String codigo, TipoEspacio tipo, String tipoEtiqueta, int capacidad) {
    }

    public record ClienteRef(Long id, String nombre, String email) {
    }

    public record Cambio(LocalDateTime fecha, String autor, String motivo, String detalle) {
    }

    public record ReservaDTO(
            Long id,
            String codigo,
            EstadoReserva estado,
            LocalDateTime inicio,
            LocalDateTime fin,
            int duracionMinutos,
            EspacioRef espacio,
            Ref piso,
            Ref edificio,
            Ref empresa,
            ClienteRef cliente,
            boolean creadaPorAdmin,
            boolean cancelable,
            LocalDateTime creadaEl,
            /** Only filled in the detail. */
            List<Cambio> cambios,
            /** The charge of the booking; null while it was not charged (or charging is off). */
            Pago pago,
            /** What the customer gets back cancelling now; only for the customer's own cancelable bookings. */
            ReintegroSiCancela reintegroSiCancela) {
    }

    public record Pago(BigDecimal monto, EstadoCobro estado, String tarjeta, BigDecimal reintegrado) {
    }

    /** {@code monto} is null when nothing was charged yet: cancelling costs nothing then. */
    public record ReintegroSiCancela(int porcentaje, BigDecimal monto) {
    }

    public record MisReservas(List<ReservaDTO> proximas, List<ReservaDTO> historial) {
    }

    /** Body of a 409 when the slot was taken: nearby free ranges of the same space and day. */
    public record FranjaTomada(String message, String code, List<FranjaDTO> alternativas) {
    }

    public record PaginaReservas(
            List<ReservaDTO> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            /** Every booking in the system, for "12 resultados de 248 reservas". */
            long totalGeneral) {
    }

    public record FiltroReservas(
            Long edificioId,
            Long pisoId,
            TipoEspacio tipo,
            LocalDate desde,
            LocalDate hasta,
            EstadoReserva estado,
            String q) {
    }

    public record BloqueAgenda(
            String tipo,
            Long id,
            Long espacioId,
            LocalDateTime inicio,
            LocalDateTime fin,
            EstadoReserva estado,
            ClienteRef cliente,
            MotivoBloqueo motivo,
            String motivoEtiqueta,
            String detalle,
            UUID grupo) {
    }

    public record AgendaPiso(
            Ref piso,
            EdificioRef edificio,
            LocalDate fecha,
            LocalDateTime ahora,
            List<EspacioBreve> espacios,
            List<BloqueAgenda> bloques) {
    }

    public enum Alcance { ESPACIO, PISO }

    public record CrearBloqueoRequest(
            @NotNull(message = "Elegí el alcance") Alcance alcance,
            Long espacioId,
            Long pisoId,
            @NotNull(message = "Elegí desde cuándo") LocalDateTime inicio,
            @NotNull(message = "Elegí hasta cuándo") LocalDateTime fin,
            @NotNull(message = "Elegí el motivo") MotivoBloqueo motivo,
            @Size(max = 200, message = "El detalle no puede superar los 200 caracteres") String detalle,
            /** Cancel the bookings in the way (full refund and a mail each); without it they stop the block. */
            Boolean cancelarReservas) {
    }

    /** A booking a block would sweep away. */
    public record ReservaAfectada(Long id, String codigo, String espacio, String cliente, LocalDateTime inicio, LocalDateTime fin) {
    }

    /** Body of the 409 of a block that falls on bookings: the client confirms and retries. */
    public record ReservasAfectadas(String message, String code, List<ReservaAfectada> reservas) {
    }

    public record BloqueoDTO(
            Long id,
            UUID grupo,
            EspacioRef espacio,
            Ref piso,
            Ref edificio,
            LocalDateTime inicio,
            LocalDateTime fin,
            MotivoBloqueo motivo,
            String motivoEtiqueta,
            String detalle,
            String creadoPor,
            LocalDateTime creadoEl) {
    }

    public record BloqueosCreados(UUID grupo, int cantidad, List<BloqueoDTO> bloqueos) {
    }

    public record ClienteBusqueda(Long id, String nombre, String email) {
    }
}
