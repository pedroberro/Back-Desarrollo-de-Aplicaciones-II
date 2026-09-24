package com.daii.backdaii.controller;

import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AdminReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AgendaPiso;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ClienteBusqueda;
import com.daii.backdaii.dto.reserva.ReservaDTOs.FiltroReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.PaginaReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;
import com.daii.backdaii.service.AdminReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Presentation layer of the bookings component, administrator side. The whole class requires
 * the permission, checked declaratively before any method runs.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('RESERVAS_GESTIONAR')")
public class AdminReservaController {

    private final AdminReservaService adminReservaService;

    @GetMapping("/reservas")
    public ResponseEntity<PaginaReservas> listar(
            @RequestParam(required = false) Long edificioId,
            @RequestParam(required = false) Long pisoId,
            @RequestParam(required = false) Long espacioId,
            @RequestParam(required = false) TipoEspacio tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        FiltroReservas filtro = new FiltroReservas(edificioId, pisoId, tipo, desde, hasta, estado, q);
        return ResponseEntity.ok(adminReservaService.listar(filtro, espacioId, page, size));
    }

    @GetMapping("/agenda")
    public ResponseEntity<AgendaPiso> agenda(
            @RequestParam Long pisoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(adminReservaService.agenda(pisoId, fecha));
    }

    @PostMapping("/reservas")
    public ResponseEntity<ReservaDTO> crear(@AuthenticationPrincipal UserDetails user,
                                            @Valid @RequestBody AdminReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminReservaService.crear(user.getUsername(), request));
    }

    @PutMapping("/reservas/{id}")
    public ResponseEntity<ReservaDTO> actualizar(@AuthenticationPrincipal UserDetails user, @PathVariable Long id,
                                                 @Valid @RequestBody AdminReservaRequest request) {
        return ResponseEntity.ok(adminReservaService.actualizar(user.getUsername(), id, request));
    }

    @DeleteMapping("/reservas/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        adminReservaService.eliminar(user.getUsername(), id);
        return ResponseEntity.ok(Map.of("message", "Reserva eliminada. La franja quedó libre."));
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<ClienteBusqueda>> buscarClientes(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(adminReservaService.buscarClientes(q));
    }
}
