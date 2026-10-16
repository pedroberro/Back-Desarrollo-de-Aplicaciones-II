package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.disponibilidad.Ocupacion;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.enums.EstadoReserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    /** Active bookings of the given spaces that touch [desde, hasta), already shaped for the engine. */
    @Query("select new com.daii.backdaii.domain.disponibilidad.Ocupacion(r.espacio.id, r.inicio, r.fin, 'RESERVA', r.id) "
            + "from Reserva r where r.espacio.id in :espacioIds and r.estado in :estados "
            + "and r.inicio < :hasta and r.fin > :desde")
    List<Ocupacion> findOcupaciones(Collection<Long> espacioIds, LocalDateTime desde, LocalDateTime hasta,
                                    Collection<EstadoReserva> estados);

    @Query("select new com.daii.backdaii.domain.disponibilidad.Ocupacion(r.espacio.id, r.inicio, r.fin, 'RESERVA', r.id) "
            + "from Reserva r where r.estado in :estados and r.inicio < :hasta and r.fin > :desde")
    List<Ocupacion> findOcupacionesEntre(LocalDateTime desde, LocalDateTime hasta, Collection<EstadoReserva> estados);

    @EntityGraph(attributePaths = {"espacio", "espacio.piso", "espacio.piso.edificio", "espacio.piso.edificio.empresa", "usuario", "creadaPor"})
    List<Reserva> findByUsuarioIdOrderByInicioAsc(Long usuarioId);

    @EntityGraph(attributePaths = {"espacio", "espacio.piso", "espacio.piso.edificio", "espacio.piso.edificio.empresa", "usuario", "creadaPor"})
    Optional<Reserva> findDetalleById(Long id);

    /**
     * Bookings whose charge entered the queue before {@code antesDe} and never resolved: still
     * PENDIENTE and without a charge. The oldest first, a page at a time.
     */
    @Query("select r from Reserva r where r.estado = :pendiente and r.cobroSolicitadoEl is not null "
            + "and r.cobroSolicitadoEl < :antesDe and not exists (select c from Cobro c where c.reserva = r) "
            + "order by r.cobroSolicitadoEl")
    List<Reserva> findCobrosSinResolver(EstadoReserva pendiente, LocalDateTime antesDe, Pageable pagina);

    /** The customer's active bookings that overlap a range: a person cannot be in two spaces at once. */
    @EntityGraph(attributePaths = {"espacio"})
    @Query("select r from Reserva r where r.usuario.id = :usuarioId and r.estado in :estados "
            + "and r.inicio < :hasta and r.fin > :desde order by r.inicio")
    List<Reserva> findDelUsuarioEnRango(Long usuarioId, LocalDateTime desde, LocalDateTime hasta,
                                        Collection<EstadoReserva> estados);

    /**
     * Locks the booking row until the transaction ends. Charging and cancelling both go through
     * here, so a cancellation never overwrites a charge in flight, nor the other way around.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findBloqueadaById(Long id);

    /** Bookings shown in a calendar or an agenda, with the customer, so the view needs one query. */
    @EntityGraph(attributePaths = {"espacio", "usuario"})
    @Query("select r from Reserva r where r.espacio.id in :espacioIds and r.inicio < :hasta and r.fin > :desde "
            + "and r.estado in :estados order by r.inicio")
    List<Reserva> findEnRango(Collection<Long> espacioIds, LocalDateTime desde, LocalDateTime hasta,
                              Collection<EstadoReserva> estados);

    @Override
    @EntityGraph(attributePaths = {"espacio", "espacio.piso", "espacio.piso.edificio", "espacio.piso.edificio.empresa", "usuario", "creadaPor"})
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);

    boolean existsByUsuarioEmailEndingWithAndInicioBetween(String dominio, LocalDateTime desde, LocalDateTime hasta);
}
