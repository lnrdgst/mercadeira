package com.mercadeira.api.familia.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;

public record SolicitacaoEntradaFamiliaResponse(
        UUID id,
        StatusSolicitacaoEntradaFamilia status,
        Instant solicitadaEm) {

    static SolicitacaoEntradaFamiliaResponse from(SolicitacaoEntradaFamilia solicitacao) {
        return new SolicitacaoEntradaFamiliaResponse(
                solicitacao.getId(), solicitacao.getStatus(), solicitacao.getSolicitadaEm());
    }
}
