package com.mercadeira.api.compra.api;

public record AcoesItemCompraResponse(
        boolean podeSolicitarRemocao,
        boolean podeDecidirRemocao,
        boolean podeRestaurarNoCarrinho) {}