package com.daii.backdaii.mapper;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "rol", expression = "java(usuario.getRol().name())")
    @Mapping(target = "estado", expression = "java(usuario.getEstado().name())")
    @Mapping(target = "permisos", expression = "java(usuario.getRol().getPermisos().stream().map(Enum::name).toList())")
    UsuarioDTO toDTO(Usuario usuario);
}
