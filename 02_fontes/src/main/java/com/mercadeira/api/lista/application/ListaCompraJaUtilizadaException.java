package com.mercadeira.api.lista.application;

public class ListaCompraJaUtilizadaException extends RuntimeException {
    public ListaCompraJaUtilizadaException() {
        super("A lista não pode ser excluída porque já possui uma compra associada.");
    }
}
