package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.EstadoCobro;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result of charging a booking. At most one per booking (see V13). */
@Entity
@Table(name = "cobros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null only if an administrator deleted the booking afterwards: the charge stays on record. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", unique = true)
    private Reserva reserva;

    /** Null when the customer had no card to charge. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medio_pago_id")
    private MedioPago medioPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCobro estado;

    /** The payment provider's id of the operation; only when approved. */
    @Column(length = 80)
    private String referencia;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    @Column(name = "procesado_el", nullable = false)
    private LocalDateTime procesadoEl;
}
