package com.mercadeira.api.familia.application;

public class SolicitacaoPendenteJaExisteException extends RuntimeException {

    public SolicitacaoPendenteJaExisteException() {
        super("Ja existe uma solicitacao pendente para esta familia e usuario.");
    }
}
