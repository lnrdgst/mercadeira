package com.mercadeira.api.usuario.repository;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.usuario.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
