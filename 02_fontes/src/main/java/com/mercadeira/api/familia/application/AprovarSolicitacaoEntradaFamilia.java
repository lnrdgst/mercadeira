package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AprovarSolicitacaoEntradaFamilia {

    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    private final MembroFamiliaRepository membroFamiliaRepository;
    private final ValidadorAdministradorFamilia validadorAdministradorFamilia;
    private final Clock clock;

    public AprovarSolicitacaoEntradaFamilia(
            SolicitacaoEntradaFamiliaRepository solicitacaoRepository,
            MembroFamiliaRepository membroFamiliaRepository,
            ValidadorAdministradorFamilia validadorAdministradorFamilia,
            Clock clock) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.validadorAdministradorFamilia = validadorAdministradorFamilia;
        this.clock = clock;
    }

    @Transactional
    public SolicitacaoEntradaFamilia aprovar(UUID solicitacaoId, UUID executorMembroId) {
        SolicitacaoEntradaFamilia solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new SolicitacaoNaoEncontradaException(solicitacaoId));
        if (solicitacao.getStatus() != StatusSolicitacaoEntradaFamilia.PENDENTE) {
            throw new SolicitacaoNaoPendenteException();
        }

        Familia familia = solicitacao.getFamilia();
        if (familia.getStatus() != StatusFamilia.ATIVA) {
            throw new FamiliaInativaException();
        }
        MembroFamilia executor = validadorAdministradorFamilia.validar(executorMembroId, familia);
        if (membroFamiliaRepository.existsByUsuario_IdAndStatus(
                solicitacao.getSolicitanteUsuario().getId(), StatusMembroFamilia.ATIVO)) {
            throw new UsuarioJaPossuiFamiliaAtivaException();
        }

        membroFamiliaRepository.save(MembroFamilia.criarMembro(
                familia, solicitacao.getSolicitanteUsuario(), clock.instant()));
        solicitacao.registrarResolucao(StatusSolicitacaoEntradaFamilia.APROVADA, executor, clock.instant());
        return solicitacao;
    }
}
