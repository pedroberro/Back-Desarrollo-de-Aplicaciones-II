package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO getMe(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return usuarioMapper.toDTO(usuario);
    }

    @Override
    @Transactional
    public UsuarioDTO updateMe(String email, UpdateMeRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        usuario.setTelefono(request.getTelefono());
        usuarioRepository.save(usuario);

        return usuarioMapper.toDTO(usuario);
    }
}