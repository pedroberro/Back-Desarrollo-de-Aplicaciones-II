package com.daii.backdaii.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** One refresh token of a session family. Only its hash is stored. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private UUID familia;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    @Column(name = "expira_el", nullable = false)
    private LocalDateTime expiraEl;

    @Column(name = "usado_el")
    private LocalDateTime usadoEl;

    @Column(name = "revocado_el")
    private LocalDateTime revocadoEl;
}
