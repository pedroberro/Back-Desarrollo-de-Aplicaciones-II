package com.daii.backdaii.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collection;

/** Spring's user plus the moment the password last changed, so tokens issued before it can be refused. */
public class UsuarioPrincipal extends User {

    private final Instant passwordCambiadaEl;

    public UsuarioPrincipal(String email, String password, boolean activo,
                            Collection<? extends GrantedAuthority> authorities, Instant passwordCambiadaEl) {
        super(email, password, activo, true, true, true, authorities);
        this.passwordCambiadaEl = passwordCambiadaEl;
    }

    /** JWT times have second precision, so a token issued in the same second as the change still counts. */
    public boolean tokenAnteriorAlCambio(Instant emitidoEl) {
        return passwordCambiadaEl != null && emitidoEl.isBefore(passwordCambiadaEl);
    }
}
