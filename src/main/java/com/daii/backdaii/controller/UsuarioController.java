package com.daii.backdaii.controller;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.service.MedioPagoService;
import com.daii.backdaii.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final MedioPagoService medioPagoService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(usuarioService.getMe(user.getUsername()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UsuarioDTO> updateMe(@AuthenticationPrincipal UserDetails user,
                                               @Valid @RequestBody UpdateMeRequest request) {
        return ResponseEntity.ok(usuarioService.updateMe(user.getUsername(), request));
    }

    @GetMapping("/me/medios-pago")
    public ResponseEntity<List<MedioPagoResumen>> listarMediosPago(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(medioPagoService.listar(user.getUsername()));
    }

    @PostMapping("/me/medios-pago")
    public ResponseEntity<MedioPagoResumen> agregarMedioPago(@AuthenticationPrincipal UserDetails user,
                                                             @Valid @RequestBody MedioPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medioPagoService.agregarTarjeta(user.getUsername(), request));
    }

    @DeleteMapping("/me/medios-pago/{id}")
    public ResponseEntity<Map<String, String>> eliminarMedioPago(@AuthenticationPrincipal UserDetails user,
                                                                 @PathVariable Long id) {
        medioPagoService.eliminar(user.getUsername(), id);
        return ResponseEntity.ok(Map.of("message", "Medio de pago eliminado correctamente."));
    }
}