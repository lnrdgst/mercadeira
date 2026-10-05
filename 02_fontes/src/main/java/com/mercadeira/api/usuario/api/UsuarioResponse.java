package com.mercadeira.api.usuario.api;

import java.util.UUID;
import java.util.List;

import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import com.mercadeira.api.usuario.domain.Usuario;

public record UsuarioResponse(UUID id, String nome, String email, List<ProvedorIdentidadeUsuario> formasAcesso,
        boolean podeAlterarEmail, boolean podeAlterarSenha) {

    static UsuarioResponse from(Usuario usuario, List<ProvedorIdentidadeUsuario> formasAcesso) {
        boolean temLocal = formasAcesso.contains(ProvedorIdentidadeUsuario.LOCAL);
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), formasAcesso, temLocal, temLocal);
    }
}
