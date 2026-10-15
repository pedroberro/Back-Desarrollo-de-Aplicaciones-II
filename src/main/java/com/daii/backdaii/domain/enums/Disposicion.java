package com.daii.backdaii.domain.enums;

/**
 * How a floor is laid out. Only a label: the plan itself is data (spaces, services and areas), so
 * nothing draws or computes differently per layout. New layouts come from scripts/disposiciones/.
 */
public enum Disposicion {
    PASILLO_CENTRAL("Pasillo central"),
    NUCLEO_CENTRAL("Núcleo central"),
    PEINE("Peine"),
    EN_L("En L con terraza");

    private final String etiqueta;

    Disposicion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
