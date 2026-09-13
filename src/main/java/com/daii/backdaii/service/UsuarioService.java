package com.daii.backdaii.service;

import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

public interface UsuarioService {

    UsuarioDTO getMe(String email);

    UsuarioDTO updateMe(String email, UpdateMeRequest request);
}