package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.TipoServicio;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "servicios_piso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicioPiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piso_id", nullable = false)
    private Piso piso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoServicio tipo;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Embedded
    private PosicionPlano posicion;
}
