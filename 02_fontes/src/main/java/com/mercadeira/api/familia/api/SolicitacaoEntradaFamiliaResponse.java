package com.mercadeira.api.familia.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.usuario.domain.Usuario;

public record SolicitacaoEntradaFamiliaResponse(
        UUID id,
        StatusSolicitacaoEntradaFamilia status,
        Instant solicitadaEm,
        SolicitanteResponse solicitante) {

    static SolicitacaoEntradaFamiliaResponse from(SolicitacaoEntradaFamilia solicitacao) {
        return new SolicitacaoEntradaFamiliaResponse(
                solicitacao.getId(),
                solicitacao.getStatus(),
                solicitacao.getSolicitadaEm(),
                SolicitanteResponse.from(solicitacao.getSolicitanteUsuario()));
    }

    public record SolicitanteResponse(UUID id, String nome, String email) {

        private static SolicitanteResponse from(Usuario usuario) {
            return new SolicitanteResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
        }
    }
}
