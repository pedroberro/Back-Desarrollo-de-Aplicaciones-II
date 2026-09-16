package com.daii.backdaii.domain.enums;

import java.util.List;

/**
 * Each role grants a fixed set of permissions. They travel to the client with the user
 * and are also registered as authorities, so endpoints can require them declaratively.
 */
public enum Rol {
    CLIENTE(List.of(
            Permiso.PERFIL_EDITAR,
            Permiso.MEDIOS_PAGO_GESTIONAR,
            Permiso.ESPACIOS_VER,
            Permiso.RESERVAS_CREAR,
            Permiso.RESERVAS_VER_PROPIAS)),
    PROVEEDOR(List.of(
            Permiso.PERFIL_EDITAR,
            Permiso.ESPACIOS_VER,
            Permiso.ESPACIOS_GESTIONAR_PROPIOS,
            Permiso.RESERVAS_VER_DE_SUS_ESPACIOS)),
    ADMIN(List.of(
            Permiso.PERFIL_EDITAR,
            Permiso.ESPACIOS_VER,
            Permiso.ESPACIOS_GESTIONAR,
            Permiso.RESERVAS_VER_TODAS,
            Permiso.USUARIOS_VER,
            Permiso.USUARIOS_BLOQUEAR));

    private final List<Permiso> permisos;

    Rol(List<Permiso> permisos) {
        this.permisos = permisos;
    }

    public List<Permiso> getPermisos() {
        return permisos;
    }
}
