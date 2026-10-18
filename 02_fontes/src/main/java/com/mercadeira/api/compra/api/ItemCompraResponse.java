package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

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
        List<LancamentoItemCompraResponse> lancamentos,
        BigDecimal precoUnitario,
        BigDecimal quantidadeComprada,
        BigDecimal valorTotal,
        Integer ordemExibicao,
        StatusItemCompra status,
        ParticipanteCompraReferenciaResponse adicionadoPor,
        Instant adicionadoEm,
        ParticipanteCompraReferenciaResponse colocadoNoCarrinhoPor,
        Instant colocadoNoCarrinhoEm,
        RemocaoItemCompraResponse remocao,
        RestauracaoItemCompraResponse restauracao,
        boolean podeGerenciarLancamentos,
        AcoesItemCompraResponse acoes) {

}
