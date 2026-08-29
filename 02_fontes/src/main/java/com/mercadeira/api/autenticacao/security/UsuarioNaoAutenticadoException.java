package com.mercadeira.api.autenticacao.security;

public class UsuarioNaoAutenticadoException extends RuntimeException {

    public UsuarioNaoAutenticadoException() {
        super("Nao ha usuario autenticado no contexto de seguranca.");
    }
}
