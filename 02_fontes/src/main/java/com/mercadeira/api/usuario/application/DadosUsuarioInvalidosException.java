package com.mercadeira.api.usuario.application;

public class DadosUsuarioInvalidosException extends RuntimeException {

    public DadosUsuarioInvalidosException(String campo) {
        super("O campo " + campo + " e obrigatorio.");
    }
}
