package com.mercadeira.api.compra.domain;

public class LancamentosLegadosIncompativeisException extends RuntimeException {
    public LancamentosLegadosIncompativeisException() {
        super("O item possui multiplos lancamentos; use o fluxo de lancamentos.");
    }
}
