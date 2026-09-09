package com.mercadeira.api.compra.application;

public class UsuarioNaoPodeDecidirRemocaoItemCompraException extends RuntimeException {

    public UsuarioNaoPodeDecidirRemocaoItemCompraException() {
        super("Somente quem colocou o item no carrinho pode decidir sua remocao.");
    }
}
