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
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AprovarSolicitacaoEntradaFamilia {

    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    private final MembroFamiliaRepository membroFamiliaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ValidadorAdministradorFamilia validadorAdministradorFamilia;
    private final Clock clock;

    public AprovarSolicitacaoEntradaFamilia(
            SolicitacaoEntradaFamiliaRepository solicitacaoRepository,
            MembroFamiliaRepository membroFamiliaRepository,
            UsuarioRepository usuarioRepository,
            ValidadorAdministradorFamilia validadorAdministradorFamilia,
            Clock clock) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.usuarioRepository = usuarioRepository;
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

        // Serializa aprovacoes do mesmo solicitante entre familias diferentes.
        Usuario solicitante = usuarioRepository.findByIdForUpdate(solicitacao.getSolicitanteUsuario().getId())
                .orElseThrow(() -> new UsuarioNaoEncontradoException(solicitacao.getSolicitanteUsuario().getId()));
        if (membroFamiliaRepository.existsByUsuario_IdAndStatus(solicitante.getId(), StatusMembroFamilia.ATIVO)) {
            throw new UsuarioJaPossuiFamiliaAtivaException();
        }

        Familia familia = solicitacao.getFamilia();
        if (familia.getStatus() != StatusFamilia.ATIVA) {
            throw new FamiliaInativaException();
        }
        MembroFamilia executor = validadorAdministradorFamilia.validar(executorMembroId, familia);
        membroFamiliaRepository.save(MembroFamilia.criarMembro(
                familia, solicitante, clock.instant()));
        solicitacao.registrarResolucao(StatusSolicitacaoEntradaFamilia.APROVADA, executor, clock.instant());
        solicitacaoRepository.findBySolicitanteUsuario_IdAndStatus(solicitante.getId(), StatusSolicitacaoEntradaFamilia.PENDENTE)
                .stream()
                .filter(pendente -> !pendente.getId().equals(solicitacao.getId()))
                .forEach(SolicitacaoEntradaFamilia::cancelarAutomaticamente);
        return solicitacao;
    }
}
