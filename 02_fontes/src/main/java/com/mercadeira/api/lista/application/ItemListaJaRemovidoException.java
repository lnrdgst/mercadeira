package com.mercadeira.api.lista.application;

public class ItemListaJaRemovidoException extends RuntimeException {
    public ItemListaJaRemovidoException() { super("O item da lista ja foi removido."); }
}
