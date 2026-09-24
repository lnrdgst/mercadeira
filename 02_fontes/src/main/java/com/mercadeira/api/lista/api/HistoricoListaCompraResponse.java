package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.List;

import com.mercadeira.api.compra.domain.Compra;
import org.springframework.data.domain.Page;

public record HistoricoListaCompraResponse(List<Item> content, int page, int size, long totalElements,
        int totalPages, boolean hasNext) {
    static HistoricoListaCompraResponse from(Page<Compra> compras) {
        return new HistoricoListaCompraResponse(compras.getContent().stream().map(Item::from).toList(),
                compras.getNumber(), compras.getSize(), compras.getTotalElements(), compras.getTotalPages(), compras.hasNext());
    }

    public record Item(ListaCompraResponse lista, Instant finalizadaEm) {
        static Item from(Compra compra) { return new Item(ListaCompraResponse.from(compra.getListaCompra()), compra.getFinalizadaEm()); }
    }
}
