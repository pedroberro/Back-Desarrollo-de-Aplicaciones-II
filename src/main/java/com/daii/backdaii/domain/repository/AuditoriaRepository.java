package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.RegistroAuditoria;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    @Query("select r from RegistroAuditoria r where (:evento is null or r.evento = :evento) "
            + "and (:usuario is null or lower(r.usuario) like lower(concat('%', cast(:usuario as string), '%'))) "
            + "order by r.fecha desc, r.id desc")
    Page<RegistroAuditoria> buscar(EventoAuditoria evento, String usuario, Pageable pagina);
}
