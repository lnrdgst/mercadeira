package com.mercadeira.api.compra.application;

public class CompraComRemocaoPendenteException extends RuntimeException {
    public CompraComRemocaoPendenteException() {
        super("Resolva as solicitacoes de remocao pendentes antes de finalizar a compra.");
    }
}