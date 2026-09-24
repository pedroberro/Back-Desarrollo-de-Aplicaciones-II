package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.enums.EstadoReserva;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "espacio_id", nullable = false)
    private Espacio espacio;

    /** Whose booking it is. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Who loaded it: the customer, or an administrator booking on their behalf. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false)
    private Usuario creadaPor;

    /** Local time of the building. */
    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @CreationTimestamp
    @Column(name = "creada_el", nullable = false, updatable = false)
    private LocalDateTime creadaEl;

    @UpdateTimestamp
    @Column(name = "actualizada_el", nullable = false)
    private LocalDateTime actualizadaEl;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("creadoEl ASC")
    @Builder.Default
    private List<ReservaCambio> cambios = new ArrayList<>();

    public String getCodigo() {
        return "R-" + id;
    }

    /** What screens show: an active booking that already ended is FINALIZADA. */
    public EstadoReserva estadoEn(LocalDateTime ahora) {
        return estado.ocupa() && !fin.isAfter(ahora) ? EstadoReserva.FINALIZADA : estado;
    }

    public void registrarCambio(Usuario autor, String motivo, String detalle) {
        cambios.add(ReservaCambio.builder().reserva(this).autor(autor).motivo(motivo).detalle(detalle).build());
    }
}
