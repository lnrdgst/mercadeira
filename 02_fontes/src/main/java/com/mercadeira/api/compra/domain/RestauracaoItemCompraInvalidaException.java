package com.mercadeira.api.compra.domain;
public class RestauracaoItemCompraInvalidaException extends RuntimeException {
    public RestauracaoItemCompraInvalidaException() {
        super("O estado ou a auditoria do item nao permite restauracao no carrinho.");
    }
}