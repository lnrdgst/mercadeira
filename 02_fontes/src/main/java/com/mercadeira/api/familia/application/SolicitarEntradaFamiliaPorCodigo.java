package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarEntradaFamiliaPorCodigo {

    private final UsuarioRepository usuarioRepository;
    private final FamiliaRepository familiaRepository;
    private final MembroFamiliaRepository membroFamiliaRepository;
    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    private final Clock clock;

    public SolicitarEntradaFamiliaPorCodigo(
            UsuarioRepository usuarioRepository,
            FamiliaRepository familiaRepository,
            MembroFamiliaRepository membroFamiliaRepository,
            SolicitacaoEntradaFamiliaRepository solicitacaoRepository,
            Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.familiaRepository = familiaRepository;
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.clock = clock;
    }

    @Transactional
    public SolicitacaoEntradaFamilia solicitar(UUID usuarioId, String codigoIngresso) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));
        Familia familia = familiaRepository.findByCodigoIngressoAndStatus(codigoIngresso, StatusFamilia.ATIVA)
                .orElseThrow(CodigoFamiliaInvalidoException::new);

        if (membroFamiliaRepository.existsByUsuario_IdAndStatus(usuarioId, StatusMembroFamilia.ATIVO)) {
            throw new UsuarioJaPossuiFamiliaAtivaException();
        }
        if (solicitacaoRepository.findByFamilia_IdAndSolicitanteUsuario_IdAndStatus(
                familia.getId(), usuarioId, StatusSolicitacaoEntradaFamilia.PENDENTE).isPresent()) {
            throw new SolicitacaoPendenteJaExisteException();
        }

        return solicitacaoRepository.save(SolicitacaoEntradaFamilia.criar(familia, usuario, clock.instant()));
    }
}
