package com.mercadeira.api.familia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoEntradaFamiliaRepository extends JpaRepository<SolicitacaoEntradaFamilia, UUID> {

    List<SolicitacaoEntradaFamilia> findByFamilia_IdAndStatusOrderBySolicitadaEmAsc(
            UUID familiaId,
            StatusSolicitacaoEntradaFamilia status);

    Optional<SolicitacaoEntradaFamilia> findByFamilia_IdAndSolicitanteUsuario_IdAndStatus(
            UUID familiaId,
            UUID solicitanteUsuarioId,
            StatusSolicitacaoEntradaFamilia status);
}
