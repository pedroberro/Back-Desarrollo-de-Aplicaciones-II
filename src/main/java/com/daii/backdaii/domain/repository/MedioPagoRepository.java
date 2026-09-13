package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.MedioPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedioPagoRepository extends JpaRepository<MedioPago, Long> {

    List<MedioPago> findByUsuarioIdAndEliminadoFalse(Long usuarioId);

    Optional<MedioPago> findByIdAndUsuarioIdAndEliminadoFalse(Long id, Long usuarioId);
}