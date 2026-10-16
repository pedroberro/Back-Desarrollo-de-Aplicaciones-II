package com.daii.backdaii.dto.auditoria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;

import java.time.LocalDateTime;
import java.util.List;

public record PaginaAuditoria(List<Registro> content, int page, int size, long totalElements, int totalPages) {

    public record Registro(Long id, LocalDateTime fecha, EventoAuditoria evento, ResultadoAuditoria resultado,
                           String usuario, String ip, String userAgent, String detalle) {
    }
}
