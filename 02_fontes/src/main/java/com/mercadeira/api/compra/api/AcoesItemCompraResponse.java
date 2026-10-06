package com.mercadeira.api.compra.api;

public record AcoesItemCompraResponse(
        boolean podeColocarNoCarrinho,
        boolean podeSolicitarRemocao,
        boolean podeRemoverDiretamente,
        boolean podeDecidirRemocao,
        boolean podeRestaurarNoCarrinho) {}
