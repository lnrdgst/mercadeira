package com.mercadeira.api.familia.application;

public class MembroSemPermissaoException extends RuntimeException {

    public MembroSemPermissaoException() {
        super("O membro nao possui permissao de administrador ativo nesta familia.");
    }
}
