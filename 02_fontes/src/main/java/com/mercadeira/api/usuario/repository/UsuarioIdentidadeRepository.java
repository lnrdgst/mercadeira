package com.mercadeira.api.usuario.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import com.mercadeira.api.usuario.domain.UsuarioIdentidade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioIdentidadeRepository extends JpaRepository<UsuarioIdentidade, UUID> {
    Optional<UsuarioIdentidade> findByProvedorAndProvedorSubject(ProvedorIdentidadeUsuario provedor, String subject);
    boolean existsByUsuarioIdAndProvedor(UUID usuarioId, ProvedorIdentidadeUsuario provedor);
    List<UsuarioIdentidade> findByUsuarioId(UUID usuarioId);
    List<UsuarioIdentidade> findByUsuarioIdIn(Collection<UUID> usuarioIds);
}
