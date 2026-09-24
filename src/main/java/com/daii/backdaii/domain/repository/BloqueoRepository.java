package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.disponibilidad.Ocupacion;
import com.daii.backdaii.domain.entity.Bloqueo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    @Query("select new com.daii.backdaii.domain.disponibilidad.Ocupacion(b.espacio.id, b.inicio, b.fin, 'BLOQUEO', b.id) "
            + "from Bloqueo b where b.espacio.id in :espacioIds and b.inicio < :hasta and b.fin > :desde")
    List<Ocupacion> findOcupaciones(Collection<Long> espacioIds, LocalDateTime desde, LocalDateTime hasta);

    @Query("select new com.daii.backdaii.domain.disponibilidad.Ocupacion(b.espacio.id, b.inicio, b.fin, 'BLOQUEO', b.id) "
            + "from Bloqueo b where b.inicio < :hasta and b.fin > :desde")
    List<Ocupacion> findOcupacionesEntre(LocalDateTime desde, LocalDateTime hasta);

    @EntityGraph(attributePaths = {"espacio", "creadoPor"})
    @Query("select b from Bloqueo b where b.espacio.id in :espacioIds and b.inicio < :hasta and b.fin > :desde order by b.inicio")
    List<Bloqueo> findEnRango(Collection<Long> espacioIds, LocalDateTime desde, LocalDateTime hasta);

    /** Current and future blocks, for the admin listing. */
    @EntityGraph(attributePaths = {"espacio", "espacio.piso", "espacio.piso.edificio", "creadoPor"})
    @Query("select b from Bloqueo b where b.fin > :desde order by b.inicio, b.espacio.codigo")
    List<Bloqueo> findVigentesDesde(LocalDateTime desde);

    List<Bloqueo> findByGrupo(UUID grupo);
}
