package com.mercadeira.api.autenticacao.api;

import java.time.Instant;

import com.mercadeira.api.autenticacao.application.ResultadoAutenticacaoGoogle;
import com.mercadeira.api.autenticacao.application.SessaoAutenticada;

public record GoogleLoginResponse(boolean vinculoNecessario, String token, Instant expiracao, String refreshToken) {
    static GoogleLoginResponse from(ResultadoAutenticacaoGoogle resultado) {
        if (resultado.vinculoNecessario()) return new GoogleLoginResponse(true, null, null, null);
        return from(resultado.sessao());
    }
    static GoogleLoginResponse from(SessaoAutenticada sessao) {
        return new GoogleLoginResponse(false, sessao.accessToken().token(), sessao.accessToken().expiraEm(), sessao.refreshToken());
    }
}
