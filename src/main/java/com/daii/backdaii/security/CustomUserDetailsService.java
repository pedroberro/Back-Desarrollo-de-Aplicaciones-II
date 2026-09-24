package com.daii.backdaii.security;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        // ROLE_X for role checks plus one authority per permission, so rules can ask for either.
        List<SimpleGrantedAuthority> authorities = Stream.concat(
                        Stream.of("ROLE_" + usuario.getRol().name()),
                        usuario.getRol().getPermisos().stream().map(Enum::name))
                .map(SimpleGrantedAuthority::new)
                .toList();

        // passwordChangedAt is written with the server's default zone, so it is read back with it.
        return new UsuarioPrincipal(
                usuario.getEmail(),
                usuario.getPassword(),
                usuario.getActivo(),
                authorities,
                usuario.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }
}
