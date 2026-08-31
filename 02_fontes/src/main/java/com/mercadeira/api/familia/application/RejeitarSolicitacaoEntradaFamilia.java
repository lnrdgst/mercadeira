package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejeitarSolicitacaoEntradaFamilia {

    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    private final ValidadorAdministradorFamilia validadorAdministradorFamilia;
    private final Clock clock;

    public RejeitarSolicitacaoEntradaFamilia(
            SolicitacaoEntradaFamiliaRepository solicitacaoRepository,
            ValidadorAdministradorFamilia validadorAdministradorFamilia,
            Clock clock) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.validadorAdministradorFamilia = validadorAdministradorFamilia;
        this.clock = clock;
    }

    @Transactional
    public SolicitacaoEntradaFamilia rejeitar(UUID familiaId, UUID solicitacaoId, UUID executorMembroId) {
        SolicitacaoEntradaFamilia solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new SolicitacaoNaoEncontradaException(solicitacaoId));
        if (!solicitacao.getFamilia().getId().equals(familiaId)) {
            throw new SolicitacaoNaoEncontradaException(solicitacaoId);
        }
        if (solicitacao.getStatus() != StatusSolicitacaoEntradaFamilia.PENDENTE) {
            throw new SolicitacaoNaoPendenteException();
        }

        MembroFamilia executor = validadorAdministradorFamilia.validar(
                executorMembroId, solicitacao.getFamilia());
        solicitacao.registrarResolucao(StatusSolicitacaoEntradaFamilia.REJEITADA, executor, clock.instant());
        return solicitacao;
    }
}
