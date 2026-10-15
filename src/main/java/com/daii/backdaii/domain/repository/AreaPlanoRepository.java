package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.AreaPlano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaPlanoRepository extends JpaRepository<AreaPlano, Long> {

    List<AreaPlano> findByPisoId(Long pisoId);
}
