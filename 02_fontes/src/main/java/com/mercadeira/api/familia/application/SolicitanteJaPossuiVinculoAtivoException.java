package com.mercadeira.api.familia.application;

public class SolicitanteJaPossuiVinculoAtivoException extends RuntimeException {
    public SolicitanteJaPossuiVinculoAtivoException() {
        super("O solicitante ja possui vinculo ativo com esta familia.");
    }
}
