package com.daii.backdaii.controller;

import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Presentation layer of the users component: the signed-in user's profile and the admin listing. */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    // Sensitive: exposes every account. Declarative rule, checked by Spring Security before the method runs.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(usuarioService.getMe(user.getUsername()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UsuarioDTO> updateMe(@AuthenticationPrincipal UserDetails user,
                                               @Valid @RequestBody UpdateMeRequest request) {
        return ResponseEntity.ok(usuarioService.updateMe(user.getUsername(), request));
    }
}
