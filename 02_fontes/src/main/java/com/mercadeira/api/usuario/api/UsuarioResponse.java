package com.mercadeira.api.usuario.api;

import java.util.UUID;

import com.mercadeira.api.usuario.domain.Usuario;

public record UsuarioResponse(UUID id, String nome, String email) {

    static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
