package com.daii.backdaii.domain.enums;

/** Bookable space kinds. The label is what every screen shows. */
public enum TipoEspacio {
    CUBICULO("Cubículo"),
    SALA_REUNIONES("Sala de reuniones"),
    SALA_PRESENTACIONES("Sala de presentaciones"),
    SALA_CONFERENCIAS("Sala de conferencias");

    private final String etiqueta;

    TipoEspacio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
