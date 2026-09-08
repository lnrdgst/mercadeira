package com.mercadeira.api.compra.domain;

public class TransicaoStatusItemCompraInvalidaException extends RuntimeException {

    public TransicaoStatusItemCompraInvalidaException(StatusItemCompra statusAtual) {
        super("O item nao pode ser colocado no carrinho no estado: " + statusAtual);
    }
}
