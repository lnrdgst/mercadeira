package com.mercadeira.api.familia.application;

public class CodigoFamiliaInvalidoException extends RuntimeException {

    public CodigoFamiliaInvalidoException() {
        super("O codigo de ingresso nao corresponde a uma familia ativa.");
    }
}
