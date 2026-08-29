package com.mercadeira.api.familia.application;

public class UsuarioJaPossuiFamiliaAtivaException extends RuntimeException {

    public UsuarioJaPossuiFamiliaAtivaException() {
        super("O usuario ja possui vinculo ativo com uma familia.");
    }
}
