package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import org.springframework.data.domain.Page;

public record HistoricoListaCompraResponse(List<Item> content, int page, int size, long totalElements,
        int totalPages, boolean hasNext) {
    static HistoricoListaCompraResponse from(Page<Compra> compras) {
        return from(compras, Map.of());
    }

    static HistoricoListaCompraResponse from(Page<Compra> compras, Map<UUID, ResumoFinanceiroCompraResponse> resumosFinanceiros) {
        return new HistoricoListaCompraResponse(compras.getContent().stream().map(compra -> Item.from(compra, resumosFinanceiros.get(compra.getId()))).toList(),
                compras.getNumber(), compras.getSize(), compras.getTotalElements(), compras.getTotalPages(), compras.hasNext());
    }

    public record Item(ListaCompraResponse lista, Instant finalizadaEm) {
        static Item from(Compra compra, ResumoFinanceiroCompraResponse resumoFinanceiro) {
            return new Item(ListaCompraResponse.from(compra.getListaCompra(), resumoFinanceiro), compra.getFinalizadaEm());
        }
    }
}
