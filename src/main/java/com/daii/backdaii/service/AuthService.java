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

    AuthResponse login(LoginRequest request);

    void logout(String token);

    void changePassword(String email, ChangePasswordRequest request);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequest request);
}
