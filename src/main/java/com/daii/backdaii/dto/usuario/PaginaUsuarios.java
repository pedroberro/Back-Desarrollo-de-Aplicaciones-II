package com.daii.backdaii.dto.usuario;

import java.util.List;

/** Paged response for the administrator's user listing. */
public record PaginaUsuarios(List<UsuarioDTO> content, int page, int size, long totalElements, int totalPages) {
}
