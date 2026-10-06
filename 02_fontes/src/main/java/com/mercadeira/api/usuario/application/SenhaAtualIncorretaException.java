package com.mercadeira.api.usuario.application;

public class SenhaAtualIncorretaException extends RuntimeException {
    public SenhaAtualIncorretaException() { super("Senha atual incorreta."); }
}
