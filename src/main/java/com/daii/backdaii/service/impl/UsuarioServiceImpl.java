package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.usuario.PaginaUsuarios;
import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.service.Precondicion;
import com.daii.backdaii.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private static final int TAMANIO_MAXIMO = 100;
    private static final int PAGINA_MAXIMA = 100_000;

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
    public UsuarioDTO updateMe(String email, UpdateMeRequest request, Precondicion precondicion) {
        Usuario usuario = usuarioRepository.findWithLockByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        precondicion.verificar(usuario.getVersion());

        usuario.setTelefono(request.getTelefono());
        usuarioRepository.save(usuario);
        auditoria.registrar(EventoAuditoria.CAMBIO_DATOS_PERSONALES, ResultadoAuditoria.EXITO, email, "Teléfono");

        return usuarioMapper.toDTO(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaUsuarios listar(int pagina, int tamanio) {
        Page<Usuario> resultado = usuarioRepository.findAllByOrderById(PageRequest.of(
                Math.clamp(pagina, 0, PAGINA_MAXIMA), Math.clamp(tamanio, 1, TAMANIO_MAXIMO)));
        return new PaginaUsuarios(resultado.getContent().stream().map(usuarioMapper::toDTO).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }
}
