package com.mercadeira.api.familia.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarMinhasSolicitacoesPendentes {
    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;

    public ConsultarMinhasSolicitacoesPendentes(SolicitacaoEntradaFamiliaRepository solicitacaoRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoEntradaFamilia> consultar(UUID usuarioId) {
        return solicitacaoRepository.findBySolicitanteUsuario_IdAndStatusOrderBySolicitadaEmAsc(
                usuarioId, StatusSolicitacaoEntradaFamilia.PENDENTE);
    }
}
