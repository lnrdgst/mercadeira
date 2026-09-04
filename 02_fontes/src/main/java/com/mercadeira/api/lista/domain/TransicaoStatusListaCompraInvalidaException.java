package com.mercadeira.api.lista.domain;

public class TransicaoStatusListaCompraInvalidaException extends RuntimeException {

    public TransicaoStatusListaCompraInvalidaException(StatusListaCompra origem, StatusListaCompra destino) {
        super("Transicao de status da lista nao permitida: " + origem + " para " + destino + ".");
    }
}
