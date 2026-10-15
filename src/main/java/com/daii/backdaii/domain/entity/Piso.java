package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.Disposicion;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "pisos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Piso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edificio_id", nullable = false)
    private Edificio edificio;

    /** 0 is the ground floor. */
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(name = "luz_natural", nullable = false)
    private Boolean luzNatural;

    /** Walls with windows, comma separated: NORTE, SUR, ESTE, OESTE. */
    @Column(nullable = false, length = 40)
    private String ventanas;

    @Column(name = "plano_columnas", nullable = false)
    private Integer planoColumnas;

    @Column(name = "plano_filas", nullable = false)
    private Integer planoFilas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Disposicion disposicion;

    public List<String> getMurosConVentana() {
        return Arrays.stream(ventanas.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
