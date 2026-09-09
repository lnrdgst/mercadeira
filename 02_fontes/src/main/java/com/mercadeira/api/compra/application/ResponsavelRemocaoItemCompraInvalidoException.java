package com.mercadeira.api.compra.application;

public class ResponsavelRemocaoItemCompraInvalidoException extends RuntimeException {

    public ResponsavelRemocaoItemCompraInvalidoException() {
        super("O responsavel pelo item no carrinho nao participa da compra.");
    }
}
