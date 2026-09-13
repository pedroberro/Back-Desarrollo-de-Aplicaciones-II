package com.daii.backdaii.service;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

public interface AuthService {

    UsuarioDTO register(RegistroRequest request);

    AuthResponse login(LoginRequest request);

    void logout(String token);

    void changePassword(String email, ChangePasswordRequest request);
}
