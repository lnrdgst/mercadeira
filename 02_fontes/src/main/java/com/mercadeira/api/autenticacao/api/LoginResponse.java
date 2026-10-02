package com.mercadeira.api.autenticacao.api;

import java.time.Instant;

import com.mercadeira.api.autenticacao.application.SessaoAutenticada;

public record LoginResponse(String token, Instant expiracao, String refreshToken) {

    static LoginResponse from(SessaoAutenticada sessao) {
        return new LoginResponse(sessao.accessToken().token(), sessao.accessToken().expiraEm(), sessao.refreshToken());
    }
}
