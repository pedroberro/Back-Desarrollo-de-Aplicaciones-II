package com.daii.backdaii.domain.enums;

public enum MotivoBloqueo {
    MANTENIMIENTO("Mantenimiento"),
    LIMPIEZA("Limpieza"),
    EVENTO("Evento"),
    OTRO("Otro");

    private final String etiqueta;

    MotivoBloqueo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
