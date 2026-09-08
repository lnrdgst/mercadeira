package com.mercadeira.api.compra.application;

public class CompraForaDeAndamentoException extends RuntimeException {

    public CompraForaDeAndamentoException() {
        super("A compra precisa estar em andamento.");
    }
}
