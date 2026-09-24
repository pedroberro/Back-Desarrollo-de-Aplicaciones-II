package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.ServicioPiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServicioPisoRepository extends JpaRepository<ServicioPiso, Long> {

    List<ServicioPiso> findByPisoIdOrderByPosicionX(Long pisoId);

    List<ServicioPiso> findByPisoIdIn(Collection<Long> pisoIds);
}
