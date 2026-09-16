package com.mercadeira.api.compra.api;

public record AcoesItemCompraResponse(
        boolean podeColocarNoCarrinho,
        boolean podeSolicitarRemocao,
        boolean podeDecidirRemocao,
        boolean podeRestaurarNoCarrinho) {}