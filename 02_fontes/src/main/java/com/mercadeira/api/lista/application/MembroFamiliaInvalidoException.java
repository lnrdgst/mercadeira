package com.mercadeira.api.lista.application;

public class MembroFamiliaInvalidoException extends RuntimeException {
    public MembroFamiliaInvalidoException() { super("O membro informado nao esta ativo na familia da lista."); }
}
