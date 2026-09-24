package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.TipoEspacio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "espacios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Espacio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piso_id", nullable = false)
    private Piso piso;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEspacio tipo;

    @Column(nullable = false)
    private Integer capacidad;

    @Column(nullable = false)
    private Boolean ventana;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal superficie;

    @Column(nullable = false, length = 20)
    private String orientacion;

    @Column(nullable = false, length = 80)
    private String ubicacion;

    @Column(nullable = false, length = 80)
    private String ruido;

    @Column(nullable = false, length = 80)
    private String climatizacion;

    /** Items read "Name · detail"; the plan shows only the name. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> equipamiento = new ArrayList<>();

    @Embedded
    private PosicionPlano posicion;
}
