package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;

public record ListaCompraResponse(UUID id, String nome, CategoriaCompra categoria, String estabelecimento,
        StatusListaCompra status, Instant criadaEm, Instant atualizadaEm, UUID criadaPorUsuarioId,
        ResumoFinanceiroCompraResponse resumoFinanceiro) {
    static ListaCompraResponse from(ListaCompra lista) {
        return from(lista, null);
    }

    static ListaCompraResponse from(ListaCompra lista, ResumoFinanceiroCompraResponse resumoFinanceiro) {
        return new ListaCompraResponse(lista.getId(), lista.getNome(), lista.getCategoria(), lista.getEstabelecimento(),
                lista.getStatus(), lista.getCriadaEm(), lista.getAtualizadaEm(), lista.getCriadaPorMembroFamilia().getUsuario().getId(),
                resumoFinanceiro);
    }
}
