package com.mercadeira.api.lista.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;

public record ItemListaResponse(UUID id, String descricao, BigDecimal quantidade, UnidadeMedida unidadeMedida,
        String marca, String observacoes, Integer ordemExibicao, Instant criadoEm, Instant atualizadoEm,
        ReferenciaPreco referenciaPreco) {
    public record ReferenciaPreco(BigDecimal precoUnitario, Instant data, String estabelecimento) { }

    static ItemListaResponse from(ItemLista item) {
        return from(item, null);
    }

    static ItemListaResponse from(ItemLista item, ReferenciaPreco referenciaPreco) {
        return new ItemListaResponse(item.getId(), item.getDescricao(), item.getQuantidade(), item.getUnidadeMedida(),
                item.getMarca(), item.getObservacoes(), item.getOrdemExibicao(), item.getCriadoEm(), item.getAtualizadoEm(),
                referenciaPreco);
    }
}
