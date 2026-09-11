package com.mercadeira.api.compra.domain;

public class FinalizacaoCompraInvalidaException extends RuntimeException {
    public FinalizacaoCompraInvalidaException(String mensagem) {
        super(mensagem);
    }
}