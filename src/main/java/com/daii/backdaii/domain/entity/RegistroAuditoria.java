package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One security event. Written once, never updated. */
@Entity
@Table(name = "auditoria")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventoAuditoria evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResultadoAuditoria resultado;

    /** Email of the account involved, when there is one. */
    @Column(length = 150)
    private String usuario;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(length = 300)
    private String detalle;
}
