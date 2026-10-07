package com.mercadeira.api.compra.domain;

public class DadosFinanceirosItemCompraInvalidosException extends RuntimeException {
    public DadosFinanceirosItemCompraInvalidosException() {
        super("Preco unitario exige quantidade comprada, e valores informados devem ser maiores que zero.");
    }
}
