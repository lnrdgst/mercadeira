package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.application.ResultadoConsultaCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;

public record CompraAtivaResponse(
        UUID id,
        UUID listaId,
        String nomeLista,
        String categoria,
        String estabelecimento,
        StatusCompra status,
        Instant iniciadaEm,
        List<ParticipanteCompraResponse> participantes,
        List<ItemCompraResponse> itens,
        ContextoUsuarioCompraResponse contextoUsuario) {

    public static CompraAtivaResponse from(ResultadoConsultaCompra resultado) {
        var compra = resultado.compra();
        return new CompraAtivaResponse(
                compra.getId(),
                compra.getListaCompra().getId(),
                compra.getNomeListaSnapshot(),
                compra.getCategoriaSnapshot(),
                compra.getEstabelecimentoSnapshot(),
                compra.getStatus(),
                compra.getIniciadaEm(),
                resultado.participantes().stream().map(participante -> new ParticipanteCompraResponse(
                        participante.getId(),
                        participante.getMembroFamilia().getId(),
                        participante.getMembroFamilia().getUsuario().getId(),
                        participante.getNomeSnapshot(),
                        participante.getPapelSnapshot(),
                        participante.getGeradoEm())).toList(),
                resultado.itens().stream().map(item -> new ItemCompraResponse(
                        item.getId(),
                        item.getItemListaOrigem() == null ? null : item.getItemListaOrigem().getId(),
                        item.isAdicionadoDuranteCompra(),
                        item.getDescricaoSnapshot(),
                        item.getQuantidadeSnapshot(),
                        item.getUnidadeMedidaSnapshot(),
                        item.getMarcaSnapshot(),
                        item.getObservacoesSnapshot(),
                        item.getOrdemExibicao(),
                        item.getStatus())).toList(),
                new ContextoUsuarioCompraResponse(resultado.participanteCompra()));
    }

    public record ParticipanteCompraResponse(
            UUID id,
            UUID membroFamiliaId,
            UUID usuarioId,
            String nome,
            PapelMembroFamilia papel,
            Instant geradoEm) {
    }

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
            StatusItemCompra status) {
    }

    public record ContextoUsuarioCompraResponse(boolean participanteCompra) {
    }
}
