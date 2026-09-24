package com.daii.backdaii.controller;

import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueoDTO;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueosCreados;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearBloqueoRequest;
import com.daii.backdaii.service.BloqueoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/bloqueos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('BLOQUEOS_GESTIONAR')")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    @GetMapping
    public ResponseEntity<List<BloqueoDTO>> listar() {
        return ResponseEntity.ok(bloqueoService.listarVigentes());
    }

    @PostMapping
    public ResponseEntity<BloqueosCreados> crear(@AuthenticationPrincipal UserDetails user,
                                                 @Valid @RequestBody CrearBloqueoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloqueoService.crear(user.getUsername(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "false") boolean grupo) {
        bloqueoService.eliminar(id, grupo);
        return ResponseEntity.ok(Map.of("message", grupo ? "Bloqueos eliminados." : "Bloqueo eliminado."));
    }
}
