package com.daii.backdaii.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/** A charge-queue message that was already handled, by its message id. */
@Entity
@Table(name = "mensajes_procesados")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeProcesado {

    @Id
    @Column(name = "mensaje_id", length = 64)
    private String mensajeId;

    @Column(name = "reserva_id")
    private Long reservaId;

    @Column(name = "procesado_el", nullable = false)
    private LocalDateTime procesadoEl;
}
