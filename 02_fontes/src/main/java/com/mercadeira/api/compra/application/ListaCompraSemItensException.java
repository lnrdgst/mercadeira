package com.mercadeira.api.compra.application;

public class ListaCompraSemItensException extends RuntimeException {

    public ListaCompraSemItensException() {
        super("A lista de compra precisa ter ao menos um item ativo para iniciar a compra.");
    }
}
