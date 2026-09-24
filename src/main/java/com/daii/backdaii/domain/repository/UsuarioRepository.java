package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.Rol;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Customers whose name or email contains the text, for the "Reservar para" search box. */
    @Query("select u from Usuario u where u.rol = :rol and (lower(u.nombre) like lower(concat('%', :texto, '%')) "
            + "or lower(u.email) like lower(concat('%', :texto, '%'))) order by u.nombre")
    List<Usuario> buscar(Rol rol, String texto, Pageable pageable);
}
