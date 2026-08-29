package com.mercadeira.api.familia.application;

public class FamiliaInativaException extends RuntimeException {

    public FamiliaInativaException() {
        super("A familia nao esta ativa.");
    }
}
