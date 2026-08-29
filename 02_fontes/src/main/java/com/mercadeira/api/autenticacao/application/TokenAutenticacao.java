package com.mercadeira.api.autenticacao.application;

import java.time.Instant;

public record TokenAutenticacao(String token, Instant expiraEm) {
}
