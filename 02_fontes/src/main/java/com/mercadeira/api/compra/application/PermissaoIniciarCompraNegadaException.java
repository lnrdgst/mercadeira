package com.mercadeira.api.compra.application;

public class PermissaoIniciarCompraNegadaException extends RuntimeException {
    public PermissaoIniciarCompraNegadaException() {
        super("Voce nao possui permissao para iniciar compras nesta familia.");
    }
}
