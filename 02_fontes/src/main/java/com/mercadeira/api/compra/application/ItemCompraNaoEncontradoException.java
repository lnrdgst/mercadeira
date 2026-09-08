package com.mercadeira.api.compra.application;

import java.util.UUID;

public class ItemCompraNaoEncontradoException extends RuntimeException {

    public ItemCompraNaoEncontradoException(UUID itemCompraId) {
        super("Item da compra nao encontrado: " + itemCompraId);
    }
}
