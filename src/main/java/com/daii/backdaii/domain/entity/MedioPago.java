package com.daii.backdaii.domain.entity;

import com.daii.backdaii.security.CampoCifrado;
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

    /** Card holder and expiry: encrypted at rest (SEC-09). */
    @Convert(converter = CampoCifrado.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String titular;

    @Convert(converter = CampoCifrado.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String vencimiento;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verificado = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean eliminado = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean principal = false;

    @CreationTimestamp
    @Column(name = "creado_el", nullable = false, updatable = false)
    private LocalDateTime creadoEl;
}