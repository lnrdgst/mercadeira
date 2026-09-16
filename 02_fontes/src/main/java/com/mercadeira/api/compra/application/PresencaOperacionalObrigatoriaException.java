package com.mercadeira.api.compra.application;

public class PresencaOperacionalObrigatoriaException extends RuntimeException {
    public PresencaOperacionalObrigatoriaException() {
        super("Declare sua presenca operacional para colocar ou restaurar itens no carrinho.");
    }
}
