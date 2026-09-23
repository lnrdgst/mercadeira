package com.mercadeira.api.lista.application;

public class ItensForaCompraInvalidosException extends RuntimeException {
    public ItensForaCompraInvalidosException() {
        super("Os itens selecionados nao podem ser reaproveitados desta compra.");
    }
}
