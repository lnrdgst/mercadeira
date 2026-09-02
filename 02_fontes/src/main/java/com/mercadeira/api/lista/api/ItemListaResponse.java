package com.mercadeira.api.lista.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;

public record ItemListaResponse(UUID id, String descricao, BigDecimal quantidade, UnidadeMedida unidadeMedida,
        String marca, String observacoes, Integer ordemExibicao, Instant criadoEm, Instant atualizadoEm) {
    static ItemListaResponse from(ItemLista item) {
        return new ItemListaResponse(item.getId(), item.getDescricao(), item.getQuantidade(), item.getUnidadeMedida(),
                item.getMarca(), item.getObservacoes(), item.getOrdemExibicao(), item.getCriadoEm(), item.getAtualizadoEm());
    }
}
