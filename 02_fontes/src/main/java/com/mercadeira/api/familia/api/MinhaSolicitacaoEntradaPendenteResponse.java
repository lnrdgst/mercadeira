package com.mercadeira.api.familia.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;

public record MinhaSolicitacaoEntradaPendenteResponse(UUID id, StatusSolicitacaoEntradaFamilia status,
        Instant solicitadaEm, FamiliaPendenteResponse familia) {

    static MinhaSolicitacaoEntradaPendenteResponse from(SolicitacaoEntradaFamilia solicitacao) {
        return new MinhaSolicitacaoEntradaPendenteResponse(solicitacao.getId(), solicitacao.getStatus(),
                solicitacao.getSolicitadaEm(), new FamiliaPendenteResponse(solicitacao.getFamilia().getId(),
                        solicitacao.getFamilia().getNome()));
    }

    public record FamiliaPendenteResponse(UUID id, String nome) { }
}
