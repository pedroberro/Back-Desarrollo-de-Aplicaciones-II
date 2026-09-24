package com.daii.backdaii.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/** Cell area on the floor plan grid, shared by spaces and floor services. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PosicionPlano {

    @Column(name = "plano_x", nullable = false)
    private Integer x;

    @Column(name = "plano_y", nullable = false)
    private Integer y;

    @Column(name = "plano_ancho", nullable = false)
    private Integer ancho;

    @Column(name = "plano_alto", nullable = false)
    private Integer alto;

    /**
     * Rough walking distance in meters between two cells: a column is about half a meter and a
     * row about three, measured along the corridors. Good enough for "Baño A · 14 m".
     */
    public int metrosHasta(PosicionPlano otra) {
        double dx = (x + ancho / 2.0 - (otra.x + otra.ancho / 2.0)) * 0.5;
        double dy = (y + alto / 2.0 - (otra.y + otra.alto / 2.0)) * 3.0;
        return (int) Math.max(1, Math.round(Math.abs(dx) + Math.abs(dy)));
    }
}
