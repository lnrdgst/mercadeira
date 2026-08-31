package com.mercadeira.api.familia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembroFamiliaRepository extends JpaRepository<MembroFamilia, UUID> {

    @EntityGraph(attributePaths = "familia")
    List<MembroFamilia> findByUsuario_IdAndStatusOrderByFamilia_NomeAsc(UUID usuarioId, StatusMembroFamilia status);

    Optional<MembroFamilia> findByFamilia_IdAndUsuario_Id(UUID familiaId, UUID usuarioId);

    @EntityGraph(attributePaths = "familia")
    Optional<MembroFamilia> findByFamilia_IdAndUsuario_IdAndStatus(
            UUID familiaId, UUID usuarioId, StatusMembroFamilia status);

    boolean existsByFamilia_IdAndUsuario_IdAndStatus(UUID familiaId, UUID usuarioId, StatusMembroFamilia status);
}
