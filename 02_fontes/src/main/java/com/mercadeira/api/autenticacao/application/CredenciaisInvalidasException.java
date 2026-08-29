package com.mercadeira.api.autenticacao.application;

public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha invalidos.");
    }
}
