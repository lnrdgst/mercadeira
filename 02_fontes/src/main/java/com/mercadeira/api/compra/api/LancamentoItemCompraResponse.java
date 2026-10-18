package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.LancamentoItemCompra;

public record LancamentoItemCompraResponse(UUID id, BigDecimal precoUnitario, BigDecimal quantidade,
        BigDecimal subtotal, Instant criadoEm, Instant atualizadoEm) {

    static LancamentoItemCompraResponse from(LancamentoItemCompra lancamento) {
        return new LancamentoItemCompraResponse(lancamento.getId(), lancamento.getPrecoUnitario(),
                lancamento.getQuantidade(), lancamento.getSubtotal(), lancamento.getCriadoEm(),
                lancamento.getAtualizadoEm());
    }
}
