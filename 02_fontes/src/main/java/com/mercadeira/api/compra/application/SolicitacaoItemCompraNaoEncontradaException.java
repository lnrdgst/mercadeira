package com.mercadeira.api.compra.application;

public class SolicitacaoItemCompraNaoEncontradaException extends RuntimeException {
    public SolicitacaoItemCompraNaoEncontradaException() {
        super("Solicitacao de item nao encontrada nesta compra.");
    }
}
