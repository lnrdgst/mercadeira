package com.mercadeira.api.lista.application;

import java.util.UUID;

public class ListaCompraNaoEncontradaException extends RuntimeException {
    public ListaCompraNaoEncontradaException(UUID id) { super("Lista de compra nao encontrada: " + id); }
}
