package com.mercadeira.api.lista.application;

public class ListaCompraForaDePreparacaoException extends RuntimeException {
    public ListaCompraForaDePreparacaoException() { super("A lista de compra nao esta em preparacao."); }
}
