package com.daii.backdaii.domain.entity;

import com.daii.backdaii.security.CampoCifrado;
import com.daii.backdaii.domain.enums.EstadoUsuario;
import com.daii.backdaii.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Builder.Default
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    /** Personal data: encrypted at rest (SEC-09). */
    @Convert(converter = CampoCifrado.class)
    @Column(columnDefinition = "TEXT")
    private String telefono;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "password_changed_at", nullable = false)
    @Builder.Default
    private LocalDateTime passwordChangedAt = LocalDateTime.now().withNano(0);

    /** TOTP secret in base32 (SEC-03). Set on the first administrator sign-in, confirmed with the first code. */
    @Convert(converter = CampoCifrado.class)
    @Column(name = "segundo_factor_secreto", columnDefinition = "TEXT")
    private String segundoFactorSecreto;

    @Column(name = "segundo_factor_activo", nullable = false)
    @Builder.Default
    private Boolean segundoFactorActivo = false;

    /** Last 30-second step whose code was accepted: the same code cannot be used twice. */
    @Column(name = "segundo_factor_ultimo_paso")
    private Long segundoFactorUltimoPaso;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
