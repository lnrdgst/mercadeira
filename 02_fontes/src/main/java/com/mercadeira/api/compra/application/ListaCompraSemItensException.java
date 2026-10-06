package com.mercadeira.api.compra.application;

public class ListaCompraSemItensException extends RuntimeException {

    public ListaCompraSemItensException() {
        super("Adicione pelo menos um item antes de iniciar a compra.");
    }
}
