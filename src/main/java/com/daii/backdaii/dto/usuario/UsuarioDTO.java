package com.daii.backdaii.dto.usuario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {

    private UUID id;
    private String fullName;
    private String email;
    private String telefono;
    private String role;
    private String status;
    private LocalDateTime createdAt;
}
