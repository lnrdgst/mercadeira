package com.mercadeira.api.compra.application;

public class UsuarioNaoParticipaDaCompraException extends RuntimeException {

    public UsuarioNaoParticipaDaCompraException() {
        super("O usuario nao participa da compra.");
    }
}
