package com.mercadeira.api.familia.application;

import java.util.UUID;

public class SolicitacaoNaoEncontradaException extends RuntimeException {

    public SolicitacaoNaoEncontradaException(UUID solicitacaoId) {
        super("Solicitacao de entrada nao encontrada: " + solicitacaoId);
    }
}
