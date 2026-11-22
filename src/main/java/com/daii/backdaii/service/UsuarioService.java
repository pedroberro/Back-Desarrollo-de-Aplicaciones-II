package com.daii.backdaii.service;

import com.daii.backdaii.dto.usuario.PaginaUsuarios;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

public interface UsuarioService {

    UsuarioDTO getMe(String email);

    default UsuarioDTO updateMe(String email, UpdateMeRequest request) {
        return updateMe(email, request, Precondicion.NINGUNA);
    }

    /** Applies the change only if the profile is still in a version the client expected (If-Match). */
    UsuarioDTO updateMe(String email, UpdateMeRequest request, Precondicion precondicion);

    PaginaUsuarios listar(int pagina, int tamanio);
}
