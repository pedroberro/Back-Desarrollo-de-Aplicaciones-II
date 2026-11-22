package com.daii.backdaii.controller;

import com.daii.backdaii.dto.usuario.PaginaUsuarios;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.service.UsuarioService;
import com.daii.backdaii.service.Versiones;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** Presentation layer of the users component: the signed-in user's profile and the admin listing. */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final Versiones versiones;

    // Sensitive: exposes every account. Declarative rule, checked by Spring Security before the method runs.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaginaUsuarios> listar(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(usuarioService.listar(page, size));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me(@AuthenticationPrincipal UserDetails user) {
        UsuarioDTO yo = usuarioService.getMe(user.getUsername());
        return ResponseEntity.ok().eTag(Etags.de(versiones.usuario(user.getUsername()))).body(yo);
    }

    @PatchMapping("/me")
    public ResponseEntity<UsuarioDTO> updateMe(@AuthenticationPrincipal UserDetails user,
                                               @Parameter(description = Etags.DESCRIPCION_IF_MATCH)
                                               @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                               @Valid @RequestBody UpdateMeRequest request) {
        UsuarioDTO yo = usuarioService.updateMe(user.getUsername(), request, Etags.precondicion(ifMatch));
        return ResponseEntity.ok().eTag(Etags.de(versiones.usuario(user.getUsername()))).body(yo);
    }
}
