package com.daii.backdaii.dto.auth;

import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A signed-in session ({@code token} and {@code usuario}), or the first half of an administrator
 * sign-in: then {@code segundoFactorRequerido} is true and {@code desafio} goes back with the code.
 * The refresh token never travels here: it is an HttpOnly cookie.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String token;
    private UsuarioDTO usuario;
    private Boolean segundoFactorRequerido;
    private String desafio;
    /** Only the first time: the secret to add to the authenticator app. */
    private AltaSegundoFactor altaSegundoFactor;

    public AuthResponse(String token, UsuarioDTO usuario) {
        this.token = token;
        this.usuario = usuario;
    }

    public record AltaSegundoFactor(String secreto, String uri) {
    }
}
