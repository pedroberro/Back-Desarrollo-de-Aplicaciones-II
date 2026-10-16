package com.daii.backdaii.service;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.dto.auditoria.PaginaAuditoria;

public interface AuditoriaService {

    /**
     * Writes a security event in its own transaction, so a failed operation is still recorded.
     * IP and user agent come from the current request, when there is one.
     *
     * @param detalle short description; never a password, token or card number
     */
    void registrar(EventoAuditoria evento, ResultadoAuditoria resultado, String usuario, String detalle);

    PaginaAuditoria buscar(EventoAuditoria evento, String usuario, int pagina, int tamanio);
}
