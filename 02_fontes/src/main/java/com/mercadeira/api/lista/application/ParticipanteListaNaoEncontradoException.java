package com.mercadeira.api.lista.application;

public class ParticipanteListaNaoEncontradoException extends RuntimeException {
    public ParticipanteListaNaoEncontradoException() { super("Participante ativo da lista nao encontrado."); }
}
