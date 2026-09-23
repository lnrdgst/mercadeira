package com.mercadeira.api.familia.application;

public class TransferenciaAdministracaoInvalidaException extends RuntimeException {

    public TransferenciaAdministracaoInvalidaException() {
        super("Não foi possível transferir a administração desta família.");
    }
}
