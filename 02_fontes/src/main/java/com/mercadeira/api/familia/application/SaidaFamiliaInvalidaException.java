package com.mercadeira.api.familia.application;

public class SaidaFamiliaInvalidaException extends RuntimeException {
    public SaidaFamiliaInvalidaException() {
        super("Não é possível sair desta família no estado atual.");
    }

    public SaidaFamiliaInvalidaException(String message) {
        super(message);
    }
}
