package com.daii.backdaii.mapper;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "id", source = "publicId")
    @Mapping(target = "fullName", source = "nombre")
    @Mapping(target = "role", expression = "java(usuario.getRol().name().toLowerCase())")
    @Mapping(target = "status", expression = "java(usuario.getEstado().name().toLowerCase())")
    UsuarioDTO toDTO(Usuario usuario);
}
