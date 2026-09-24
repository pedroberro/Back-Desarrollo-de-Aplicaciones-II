package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Edificio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EdificioRepository extends JpaRepository<Edificio, Long> {

    @EntityGraph(attributePaths = "empresa")
    @Query("select e from Edificio e order by e.empresa.nombre, e.nombre")
    List<Edificio> findAllConEmpresa();

    @EntityGraph(attributePaths = "empresa")
    List<Edificio> findByEmpresaIdOrderByNombre(Long empresaId);

    @EntityGraph(attributePaths = "empresa")
    Optional<Edificio> findConEmpresaById(Long id);
}
