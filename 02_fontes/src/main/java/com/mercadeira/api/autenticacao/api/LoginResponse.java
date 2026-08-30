package com.mercadeira.api.autenticacao.api;

import java.time.Instant;

import com.mercadeira.api.autenticacao.application.TokenAutenticacao;

public record LoginResponse(String token, Instant expiracao) {

    static LoginResponse from(TokenAutenticacao token) {
        return new LoginResponse(token.token(), token.expiraEm());
    }
}
