package com.mercadeira.api.compra.application;

import java.util.UUID;

public class CompraNaoEncontradaException extends RuntimeException {
    public CompraNaoEncontradaException(UUID listaId) {
        super("Compra nao encontrada para a lista: " + listaId);
    }
}
