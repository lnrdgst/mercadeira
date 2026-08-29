package com.mercadeira.api.familia.repository;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembroFamiliaRepository extends JpaRepository<MembroFamilia, UUID> {

    Optional<MembroFamilia> findByUsuario_IdAndStatus(UUID usuarioId, StatusMembroFamilia status);

    Optional<MembroFamilia> findByFamilia_IdAndUsuario_Id(UUID familiaId, UUID usuarioId);

    boolean existsByUsuario_IdAndStatus(UUID usuarioId, StatusMembroFamilia status);
}
