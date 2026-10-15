package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Cobro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

    boolean existsByReservaId(Long reservaId);

    Optional<Cobro> findByReservaId(Long reservaId);

    List<Cobro> findByReservaIdIn(Collection<Long> reservaIds);
}
