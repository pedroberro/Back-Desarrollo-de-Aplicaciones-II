package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
}
