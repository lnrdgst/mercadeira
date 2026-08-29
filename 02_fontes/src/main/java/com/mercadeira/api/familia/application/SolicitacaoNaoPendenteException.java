package com.mercadeira.api.familia.application;

public class SolicitacaoNaoPendenteException extends RuntimeException {

    public SolicitacaoNaoPendenteException() {
        super("A solicitacao de entrada nao esta pendente.");
    }
}
