package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.compra.api.AlertaContinuidadeCompraResponse;

public record ListaCompraResponse(UUID id, String nome, CategoriaCompra categoria, String estabelecimento,
        StatusListaCompra status, Instant criadaEm, Instant atualizadaEm, UUID criadaPorUsuarioId,
        ResumoFinanceiroCompraResponse resumoFinanceiro, AlertaContinuidadeCompraResponse alertaContinuidade) {
    static ListaCompraResponse from(ListaCompra lista) {
        return from(lista, null, null);
    }

    static ListaCompraResponse from(ListaCompra lista, ResumoFinanceiroCompraResponse resumoFinanceiro) {
        return from(lista, resumoFinanceiro, null);
    }

    static ListaCompraResponse from(ListaCompra lista, ResumoFinanceiroCompraResponse resumoFinanceiro,
            AlertaContinuidadeCompraResponse alertaContinuidade) {
        return new ListaCompraResponse(lista.getId(), lista.getNome(), lista.getCategoria(), lista.getEstabelecimento(),
                lista.getStatus(), lista.getCriadaEm(), lista.getAtualizadaEm(), lista.getCriadaPorMembroFamilia().getUsuario().getId(),
                resumoFinanceiro, alertaContinuidade);
    }
}
