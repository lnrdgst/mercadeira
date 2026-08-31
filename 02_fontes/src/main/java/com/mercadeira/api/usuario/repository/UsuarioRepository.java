package com.mercadeira.api.usuario.repository;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.usuario.domain.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select usuario from Usuario usuario where usuario.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEmail(String email);
}
