package com.mercadeira.api.lista.application;

public class OrdemItensInvalidaException extends RuntimeException {
    public OrdemItensInvalidaException() { super("A ordem informada nao representa os itens ativos da lista."); }
}
