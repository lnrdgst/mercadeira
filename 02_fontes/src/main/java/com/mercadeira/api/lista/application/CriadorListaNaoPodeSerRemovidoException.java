package com.mercadeira.api.lista.application;

public class CriadorListaNaoPodeSerRemovidoException extends RuntimeException {
    public CriadorListaNaoPodeSerRemovidoException() { super("O criador da lista nao pode ser removido dos participantes."); }
}
