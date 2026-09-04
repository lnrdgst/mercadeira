package com.mercadeira.api.compra.application;

public class ListaCompraSemParticipantesException extends RuntimeException {

    public ListaCompraSemParticipantesException() {
        super("A lista de compra precisa ter ao menos um participante ativo para iniciar a compra.");
    }
}
