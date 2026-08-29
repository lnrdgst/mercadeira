package com.mercadeira.api.familia.application;

import java.util.UUID;

public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(UUID usuarioId) {
        super("Usuario nao encontrado: " + usuarioId);
    }
}
