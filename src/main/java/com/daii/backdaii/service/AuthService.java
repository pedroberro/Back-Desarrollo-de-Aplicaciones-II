package com.daii.backdaii.service;

import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.ForgotPasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

public interface AuthService {

    UsuarioDTO register(RegistroRequest request);

    boolean emailDisponible(String email);

    AuthResponse login(LoginRequest request);

    /** Confirms the account behind a single-use link and signs the user in. */
    AuthResponse confirmarEmail(String token);

    void reenviarConfirmacion(String email);

    void logout(String token);

    void changePassword(String email, ChangePasswordRequest request);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequest request);
}
