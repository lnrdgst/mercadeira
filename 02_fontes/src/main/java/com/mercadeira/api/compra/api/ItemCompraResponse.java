package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
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
        Instant colocadoNoCarrinhoEm) {

    static ItemCompraResponse from(ItemCompra item, List<ParticipanteCompra> participantes) {
        UUID autorId = item.getAdicionadoPorParticipanteCompra() == null
                ? null
                : item.getAdicionadoPorParticipanteCompra().getId();
        UUID marcadorMembroId = item.getMarcadoPorMembroFamilia() == null
                ? null
                : item.getMarcadoPorMembroFamilia().getId();
        return new ItemCompraResponse(
                item.getId(),
                item.getItemListaOrigem() == null ? null : item.getItemListaOrigem().getId(),
                item.isAdicionadoDuranteCompra(),
                item.getDescricaoSnapshot(),
                item.getQuantidadeSnapshot(),
                item.getUnidadeMedidaSnapshot(),
                item.getMarcaSnapshot(),
                item.getObservacoesSnapshot(),
                item.getOrdemExibicao(),
                item.getStatus(),
                localizarPorId(participantes, autorId),
                item.getAdicionadoEm(),
                localizarPorMembroId(participantes, marcadorMembroId),
                item.getMarcadoEm());
    }

    private static ParticipanteCompraReferenciaResponse localizarPorId(
            List<ParticipanteCompra> participantes, UUID participanteId) {
        if (participanteId == null) {
            return null;
        }
        return participantes.stream()
                .filter(participante -> participante.getId().equals(participanteId))
                .findFirst()
                .map(ParticipanteCompraReferenciaResponse::from)
                .orElse(null);
    }

    private static ParticipanteCompraReferenciaResponse localizarPorMembroId(
            List<ParticipanteCompra> participantes, UUID membroFamiliaId) {
        if (membroFamiliaId == null) {
            return null;
        }
        return participantes.stream()
                .filter(participante -> participante.getMembroFamilia().getId().equals(membroFamiliaId))
                .findFirst()
                .map(ParticipanteCompraReferenciaResponse::from)
                .orElse(null);
    }
}
