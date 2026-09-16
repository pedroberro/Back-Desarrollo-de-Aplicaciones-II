package com.daii.backdaii.dto.usuario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {

    private Long id;
    private UUID publicId;
    private String nombre;
    private String email;
    private String telefono;
    private String rol;
    private String estado;
    private LocalDateTime createdAt;
    /** Derived from the role; lets the client show or hide actions. */
    private List<String> permisos;
}
