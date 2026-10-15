package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Cobro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

    boolean existsByReservaId(Long reservaId);
}
