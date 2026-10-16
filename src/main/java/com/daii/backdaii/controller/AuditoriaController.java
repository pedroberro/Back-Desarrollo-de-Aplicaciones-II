package com.daii.backdaii.controller;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.dto.auditoria.PaginaAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only access to the security audit log, for administrators. */
@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoria;

    @GetMapping
    public ResponseEntity<PaginaAuditoria> buscar(@RequestParam(required = false) EventoAuditoria evento,
                                                  @RequestParam(required = false) String usuario,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(auditoria.buscar(evento, usuario, page, size));
    }
}
