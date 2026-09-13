package com.daii.backdaii.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "medios_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedioPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(name = "numero_tarjeta_masked", nullable = false, length = 23)
    private String numeroTarjetaMasked;

    @Column(nullable = false, length = 100)
    private String titular;

    @Column(nullable = false, length = 5)
    private String vencimiento;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verificado = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean eliminado = false;

    @CreationTimestamp
    @Column(name = "creado_el", nullable = false, updatable = false)
    private LocalDateTime creadoEl;
}