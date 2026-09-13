package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.TokenBlacklist;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.TokenBlacklistRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.security.JwtTokenProvider;
import com.daii.backdaii.service.AuthService;
import com.daii.backdaii.service.MedioPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioMapper usuarioMapper;
    private final MedioPagoService medioPagoService;

    @Override
    @Transactional
    public UsuarioDTO register(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }

        Rol rol;
        try {
            rol = Rol.valueOf(request.getRol().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Rol inválido: " + request.getRol());
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(request.getEmail())
                .telefono(request.getTelefono())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(rol)
                .build();

        usuario = usuarioRepository.save(usuario);

        if (request.getMedioPago() != null) {
            medioPagoService.agregarTarjeta(usuario.getEmail(), request.getMedioPago());
        }

        return usuarioMapper.toDTO(usuario);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String token = jwtTokenProvider.generateToken(
                usuario.getEmail(), usuario.getId(), usuario.getRol().name());

        return new AuthResponse(token, usuarioMapper.toDTO(usuario));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }

        usuario.setPassword(passwordEncoder.encode(request.getPasswordNueva()));
        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            if (!tokenBlacklistRepository.existsByToken(token)) {
                tokenBlacklistRepository.save(TokenBlacklist.builder()
                        .token(token)
                        .expiresAt(jwtTokenProvider.extractExpiration(token))
                        .build());
            }
        }
    }
}
