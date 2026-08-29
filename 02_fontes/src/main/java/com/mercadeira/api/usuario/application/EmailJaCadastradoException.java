package com.mercadeira.api.usuario.application;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("Ja existe um usuario cadastrado com este e-mail.");
    }
}
