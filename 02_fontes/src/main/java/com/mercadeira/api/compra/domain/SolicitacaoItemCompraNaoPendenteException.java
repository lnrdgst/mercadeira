package com.mercadeira.api.compra.domain;

public class SolicitacaoItemCompraNaoPendenteException extends RuntimeException {
    public SolicitacaoItemCompraNaoPendenteException() {
        super("A solicitacao de item ja foi decidida. Atualize a compra.");
    }
}
