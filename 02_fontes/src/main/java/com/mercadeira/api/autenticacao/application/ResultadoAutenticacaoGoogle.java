package com.mercadeira.api.autenticacao.application;

public record ResultadoAutenticacaoGoogle(SessaoAutenticada sessao, boolean vinculoNecessario) {
    public static ResultadoAutenticacaoGoogle autenticado(SessaoAutenticada sessao) { return new ResultadoAutenticacaoGoogle(sessao, false); }
    public static ResultadoAutenticacaoGoogle requerVinculo() { return new ResultadoAutenticacaoGoogle(null, true); }
}
