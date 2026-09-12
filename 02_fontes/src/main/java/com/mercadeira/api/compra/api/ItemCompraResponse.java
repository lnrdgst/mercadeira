package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.StatusItemCompra;

public record ItemCompraResponse(
        UUID id,
        UUID itemListaOrigemId,
        boolean adicionadoDuranteCompra,
        String descricao,
        BigDecimal quantidade,
        String unidadeMedida,
        String marca,
        String observacoes,
        Integer ordemExibicao,
        StatusItemCompra status,
        ParticipanteCompraReferenciaResponse adicionadoPor,
        Instant adicionadoEm,
        ParticipanteCompraReferenciaResponse colocadoNoCarrinhoPor,
        Instant colocadoNoCarrinhoEm,
        RemocaoItemCompraResponse remocao,
        RestauracaoItemCompraResponse restauracao,
        AcoesItemCompraResponse acoes) {

}
