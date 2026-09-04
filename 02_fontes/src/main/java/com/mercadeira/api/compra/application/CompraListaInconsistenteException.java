package com.mercadeira.api.compra.application;

public class CompraListaInconsistenteException extends RuntimeException {

    public CompraListaInconsistenteException() {
        super("O estado da lista e da compra associada e inconsistente.");
    }
}
