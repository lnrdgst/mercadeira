package com.mercadeira.api.usuario.application;

public class FormaAcessoNaoDisponivelException extends RuntimeException {
    public FormaAcessoNaoDisponivelException() { super("Esta conta e gerenciada pela Conta Google."); }
}
