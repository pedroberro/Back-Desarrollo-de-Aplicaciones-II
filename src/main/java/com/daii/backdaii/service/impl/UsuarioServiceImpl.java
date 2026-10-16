package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final AuditoriaService auditoria;

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
        auditoria.registrar(EventoAuditoria.CAMBIO_DATOS_PERSONALES, ResultadoAuditoria.EXITO, email, "Teléfono");

        return usuarioMapper.toDTO(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar() {
        return usuarioRepository.findAll(Sort.by("id")).stream()
                .map(usuarioMapper::toDTO)
                .toList();
    }
}
