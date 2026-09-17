package com.daii.backdaii.controller;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;
import com.daii.backdaii.service.MedioPagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Presentation layer of the payment methods component. Every operation works on the
 * cards of the signed-in user, taken from the token, never from the request.
 */
@RestController
@RequestMapping("/api/usuarios/me/medios-pago")
@RequiredArgsConstructor
public class MedioPagoController {

    private final MedioPagoService medioPagoService;

    @GetMapping
    public ResponseEntity<List<MedioPagoResumen>> listar(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(medioPagoService.listar(user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<MedioPagoResumen> agregar(@AuthenticationPrincipal UserDetails user,
                                                    @Valid @RequestBody MedioPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medioPagoService.agregarTarjeta(user.getUsername(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedioPagoResumen> actualizar(@AuthenticationPrincipal UserDetails user,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody MedioPagoUpdateRequest request) {
        return ResponseEntity.ok(medioPagoService.actualizar(user.getUsername(), id, request));
    }

    @PatchMapping("/{id}/principal")
    public ResponseEntity<List<MedioPagoResumen>> marcarPrincipal(@AuthenticationPrincipal UserDetails user,
                                                                  @PathVariable Long id) {
        return ResponseEntity.ok(medioPagoService.marcarPrincipal(user.getUsername(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@AuthenticationPrincipal UserDetails user,
                                                        @PathVariable Long id) {
        medioPagoService.eliminar(user.getUsername(), id);
        return ResponseEntity.ok(Map.of("message", "Medio de pago eliminado correctamente."));
    }
}
