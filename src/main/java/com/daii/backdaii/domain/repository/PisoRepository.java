package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Piso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PisoRepository extends JpaRepository<Piso, Long> {

    /** Top floor first, like the building is drawn. */
    List<Piso> findByEdificioIdOrderByNumeroDesc(Long edificioId);

    List<Piso> findByEdificioIdInOrderByNumeroDesc(Collection<Long> edificioIds);

    @EntityGraph(attributePaths = {"edificio", "edificio.empresa"})
    Optional<Piso> findConEdificioById(Long id);
}
