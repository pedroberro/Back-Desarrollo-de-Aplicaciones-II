package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Espacio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EspacioRepository extends JpaRepository<Espacio, Long> {

    List<Espacio> findByPisoIdOrderByCodigo(Long pisoId);

    List<Espacio> findByPisoIdInOrderByCodigo(Collection<Long> pisoIds);

    @EntityGraph(attributePaths = {"piso", "piso.edificio", "piso.edificio.empresa"})
    Optional<Espacio> findConUbicacionById(Long id);

    /**
     * Locks the space row until the transaction ends. Every write that takes a slot of this
     * space goes through here first, so two concurrent bookings of it run one after the other
     * and the second one sees the first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Espacio e join fetch e.piso p join fetch p.edificio where e.id = :id")
    Optional<Espacio> findParaReservarById(Long id);

    @Query("select new com.daii.backdaii.domain.repository.EspacioUbicacion(e.id, p.id, ed.id, ed.empresa.id) "
            + "from Espacio e join e.piso p join p.edificio ed")
    List<EspacioUbicacion> findUbicaciones();

    @Query("select new com.daii.backdaii.domain.repository.EspacioUbicacion(e.id, p.id, ed.id, ed.empresa.id) "
            + "from Espacio e join e.piso p join p.edificio ed where ed.empresa.id = :empresaId")
    List<EspacioUbicacion> findUbicacionesDeEmpresa(Long empresaId);

    @Query("select new com.daii.backdaii.domain.repository.EspacioUbicacion(e.id, p.id, ed.id, ed.empresa.id) "
            + "from Espacio e join e.piso p join p.edificio ed where ed.id = :edificioId")
    List<EspacioUbicacion> findUbicacionesDeEdificio(Long edificioId);
}
