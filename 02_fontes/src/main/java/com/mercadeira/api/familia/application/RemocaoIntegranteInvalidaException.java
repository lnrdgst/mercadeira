package com.mercadeira.api.familia.application;

public class RemocaoIntegranteInvalidaException extends RuntimeException {

    public RemocaoIntegranteInvalidaException() {
        super("Não foi possível remover este integrante da família.");
    }

    public RemocaoIntegranteInvalidaException(String message) {
        super(message);
    }
}
