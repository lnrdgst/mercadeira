package com.mercadeira.api.familia.application;

public class CodigoIngressoIndisponivelException extends RuntimeException {

    public CodigoIngressoIndisponivelException() {
        super("Nao foi possivel gerar um codigo de ingresso unico.");
    }
}
