package com.daii.backdaii.domain.entity;

import com.daii.backdaii.domain.disponibilidad.Horario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "edificios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Edificio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String direccion;

    @Column(nullable = false, length = 80)
    private String localidad;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Column(nullable = false, length = 10)
    private String tono;

    public Horario getHorario() {
        return new Horario(horaApertura, horaCierre);
    }
}
