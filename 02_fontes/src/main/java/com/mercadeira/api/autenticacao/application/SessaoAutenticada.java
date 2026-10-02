package com.mercadeira.api.autenticacao.application;

public record SessaoAutenticada(TokenAutenticacao accessToken, String refreshToken) { }
