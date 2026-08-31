package com.mercadeira.api.lista.application;

import java.util.UUID;

public class ItemListaNaoEncontradoException extends RuntimeException {
    public ItemListaNaoEncontradoException(UUID id) { super("Item da lista nao encontrado: " + id); }
}
