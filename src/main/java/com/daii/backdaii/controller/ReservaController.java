package com.daii.backdaii.controller;

import com.daii.backdaii.domain.enums.Permiso;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.MisReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;
import com.daii.backdaii.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** Presentation layer of the bookings component, customer side. The customer always comes from the token. */
@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVAS_CREAR')")
    public ResponseEntity<ReservaDTO> crear(@AuthenticationPrincipal UserDetails user,
                                            @Valid @RequestBody CrearReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(user.getUsername(), request));
    }

    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('RESERVAS_VER_PROPIAS')")
    public ResponseEntity<MisReservas> misReservas(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(reservaService.misReservas(user.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('RESERVAS_VER_PROPIAS', 'RESERVAS_VER_TODAS')")
    public ResponseEntity<ReservaDTO> detalle(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        boolean esAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Permiso.RESERVAS_VER_TODAS.name()));
        return ResponseEntity.ok(reservaService.detalle(user.getUsername(), id, esAdmin));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('RESERVAS_CREAR')")
    public ResponseEntity<ReservaDTO> cancelar(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(user.getUsername(), id));
    }
}
