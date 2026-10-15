package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.TipoArea;
import jakarta.persistence.*;
import lombok.*;

/** A corridor, the core, a support area or a terrace: part of the drawing of a floor. */
@Entity
@Table(name = "areas_plano")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaPlano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piso_id", nullable = false)
    private Piso piso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoArea tipo;

    @Column(nullable = false, length = 40)
    private String etiqueta;

    @Embedded
    private PosicionPlano posicion;
}
