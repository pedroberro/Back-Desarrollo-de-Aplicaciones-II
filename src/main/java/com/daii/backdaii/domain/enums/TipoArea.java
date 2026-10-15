package com.daii.backdaii.domain.enums;

/** Structural parts of a floor plan. Drawn, never booked. */
public enum TipoArea {
    /** Where people walk. */
    PASILLO,
    /** Elevators and stairs. */
    NUCLEO,
    /** Lockers, printers, building services: what the spaces leave free. */
    APOYO,
    /** Terrace or patio: open air, the spaces next to it have windows onto it. */
    EXTERIOR
}
