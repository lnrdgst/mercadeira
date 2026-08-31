package com.mercadeira.api.familia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;

public interface SolicitacaoEntradaFamiliaRepository extends JpaRepository<SolicitacaoEntradaFamilia, UUID> {

    @EntityGraph(attributePaths = "solicitanteUsuario")
    List<SolicitacaoEntradaFamilia> findByFamilia_IdAndStatusOrderBySolicitadaEmAsc(
            UUID familiaId,
            StatusSolicitacaoEntradaFamilia status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "familia", "solicitanteUsuario" })
    Optional<SolicitacaoEntradaFamilia> findById(UUID id);

    Optional<SolicitacaoEntradaFamilia> findByFamilia_IdAndSolicitanteUsuario_IdAndStatus(
            UUID familiaId,
            UUID solicitanteUsuarioId,
            StatusSolicitacaoEntradaFamilia status);

    List<SolicitacaoEntradaFamilia> findBySolicitanteUsuario_IdAndStatus(UUID solicitanteUsuarioId,
            StatusSolicitacaoEntradaFamilia status);

    @EntityGraph(attributePaths = "familia")
    List<SolicitacaoEntradaFamilia> findBySolicitanteUsuario_IdAndStatusOrderBySolicitadaEmAsc(
            UUID solicitanteUsuarioId, StatusSolicitacaoEntradaFamilia status);
}
