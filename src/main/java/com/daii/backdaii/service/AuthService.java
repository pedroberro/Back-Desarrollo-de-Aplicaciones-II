package com.daii.backdaii.service;

import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.auth.ResultadoAuth;
import com.daii.backdaii.dto.auth.SegundoFactorRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

public interface AuthService {

    UsuarioDTO register(RegistroRequest request);

    boolean emailDisponible(String email);

    /** A session, or for administrators the second-factor challenge (SEC-03). */
    ResultadoAuth login(LoginRequest request);

    /** Second half of an administrator sign-in: the TOTP code. Wrong code: 401. */
    ResultadoAuth segundoFactor(SegundoFactorRequest request);

    /** Confirms the account behind a single-use link and signs the user in. */
    ResultadoAuth confirmarEmail(String token);

    void reenviarConfirmacion(String email);

    /** Spends the refresh token and issues a new pair (SEC-02). */
    ResultadoAuth refresh(String refresh);

    void logout(String authorizationHeader, String refresh);

    /** Returns a new session: every other session of the account ends. */
    ResultadoAuth changePassword(String email, ChangePasswordRequest request);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequest request);
}
