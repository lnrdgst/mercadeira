package com.mercadeira.api.lista.application;

public class UsuarioNaoParticipaDaListaException extends RuntimeException {
    public UsuarioNaoParticipaDaListaException() { super("O usuario nao participa ativamente da lista."); }
}
