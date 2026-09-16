package com.daii.backdaii.mapper;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "publicId", source = "publicId")
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "rol", expression = "java(usuario.getRol().name())")
    @Mapping(target = "estado", expression = "java(usuario.getEstado().name())")
    UsuarioDTO toDTO(Usuario usuario);
}
