package com.daii.backdaii.dto.catalogo;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.ReglasReserva;
import com.daii.backdaii.domain.enums.EstadoEspacio;
import com.daii.backdaii.domain.enums.TipoArea;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.domain.enums.TipoServicio;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Responses of the catalog component (empresa > edificio > piso > espacio). They are small,
 * read-only shapes, so they live together as records instead of one file each.
 */
public final class CatalogoDTOs {

    private CatalogoDTOs() {
    }

    public record Ref(Long id, String nombre) {
    }

    public record FranjaDTO(LocalDateTime inicio, LocalDateTime fin) {
        public static FranjaDTO de(Franja franja) {
            return franja == null ? null : new FranjaDTO(franja.inicio(), franja.fin());
        }
    }

    /** "Cocina", "Sala de estar", "2 baños", "Luz natural". */
    public record Comodidad(String tipo, String etiqueta) {
    }

    public record EmpresaResumen(
            Long id,
            String nombre,
            String rubro,
            String tono,
            int edificios,
            List<String> localidades,
            int espaciosLibres,
            /** Day each building's count refers to: today, or tomorrow once it closed. */
            List<LocalDate> jornadas) {
    }

    public record EdificioResumen(
            Long id,
            String nombre,
            String direccion,
            String localidad,
            @JsonFormat(pattern = "HH:mm") LocalTime apertura,
            @JsonFormat(pattern = "HH:mm") LocalTime cierre,
            String tono,
            int pisos,
            int espacios,
            int espaciosLibres,
            LocalDate jornada) {
    }

    public record EmpresaDetalle(EmpresaResumen empresa, List<EdificioResumen> edificios) {
    }

    public record PisoResumen(
            Long id,
            int numero,
            String nombre,
            int espacios,
            int cubiculos,
            int salas,
            List<Comodidad> comodidades,
            int libres,
            /** Label of the floor's layout, e.g. "Núcleo central". */
            String disposicion) {
    }

    public record EdificioDetalle(
            EdificioResumen edificio,
            Ref empresa,
            /** Slot the floor counters refer to: now, or the next opening when the building is closed. */
            FranjaDTO franja,
            boolean abiertoAhora,
            int libres,
            List<PisoResumen> pisos) {
    }

    public record EdificioRef(
            Long id,
            String nombre,
            String direccion,
            @JsonFormat(pattern = "HH:mm") LocalTime apertura,
            @JsonFormat(pattern = "HH:mm") LocalTime cierre) {
    }

    public record PisoInfo(
            Long id,
            int numero,
            String nombre,
            int columnas,
            int filas,
            /** Label of the layout, e.g. "Núcleo central". The plan is drawn from the areas, not from this. */
            String disposicion,
            List<String> ventanas,
            List<Comodidad> comodidades) {
    }

    public record Posicion(int x, int y, int ancho, int alto) {
    }

    public record EspacioEnPlano(
            Long id,
            String codigo,
            TipoEspacio tipo,
            String tipoEtiqueta,
            int capacidad,
            boolean ventana,
            /** Side of the window: Norte, Sur, Este, Oeste; Interior when it has none. */
            String orientacion,
            /** Short names ("Monitor", "Wi-Fi") for the tooltip and the list. */
            List<String> equipamiento,
            Posicion posicion,
            EstadoEspacio estado,
            /** What makes it busy in the slot, e.g. "Ocupado · 10:00–12:00". */
            FranjaDTO ocupacion,
            /** First free range from the slot on; null when nothing is free in two weeks. */
            FranjaDTO proximoLibre,
            boolean cumpleFiltros) {
    }

    public record ServicioEnPlano(Long id, TipoServicio tipo, String nombre, Posicion posicion) {
    }

    /** Corridor, core, support area or terrace. */
    public record AreaEnPlano(Long id, TipoArea tipo, String etiqueta, Posicion posicion) {
    }

    public record ResumenPlano(int total, int libres, int ocupados, int bloqueados) {
    }

    public record PisoLibres(Long id, String nombre, int libres) {
    }

    public record PlanoPiso(
            PisoInfo piso,
            EdificioRef edificio,
            Ref empresa,
            FranjaDTO franja,
            List<EspacioEnPlano> espacios,
            List<ServicioEnPlano> servicios,
            List<AreaEnPlano> areas,
            /** Counted over the spaces that pass the filters. */
            ResumenPlano resumen,
            /** Same slot on the other floors of the building. */
            List<PisoLibres> otrosPisos,
            /** Next slot of the same length, same day, with something free; null if none. */
            FranjaDTO sugerencia) {
    }

    public record ServicioCercano(TipoServicio tipo, String nombre, int metros) {
    }

    public record PisoRef(Long id, int numero, String nombre) {
    }

    public record EspacioDetalle(
            Long id,
            String codigo,
            TipoEspacio tipo,
            String tipoEtiqueta,
            int capacidad,
            boolean ventana,
            String descripcion,
            BigDecimal superficie,
            String orientacion,
            String ubicacion,
            String ruido,
            String climatizacion,
            List<String> equipamiento,
            PisoRef piso,
            EdificioRef edificio,
            Ref empresa,
            List<ServicioCercano> serviciosCercanos,
            ReglasReserva.Condiciones condiciones) {
    }

    /** One occupation in a space calendar. Customers only see details of their own bookings. */
    public record OcupacionCalendario(
            String tipo,
            LocalDateTime inicio,
            LocalDateTime fin,
            boolean propia,
            Long reservaId,
            String codigo,
            String estado,
            String cliente,
            String motivo) {
    }

    public record AgendaEspacio(
            Long espacioId,
            @JsonFormat(pattern = "HH:mm") LocalTime apertura,
            @JsonFormat(pattern = "HH:mm") LocalTime cierre,
            LocalDate desde,
            LocalDate hasta,
            LocalDateTime ahora,
            List<OcupacionCalendario> ocupaciones) {
    }

    public record EspacioBreve(Long id, String codigo, TipoEspacio tipo, String tipoEtiqueta, int capacidad) {
    }

    public record EdificioConPisos(
            Long id,
            String nombre,
            String empresa,
            @JsonFormat(pattern = "HH:mm") LocalTime apertura,
            @JsonFormat(pattern = "HH:mm") LocalTime cierre,
            List<PisoRef> pisos) {
    }
}
