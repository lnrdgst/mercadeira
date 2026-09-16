package com.mercadeira.api.compra.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.compra.application.ResultadoConsultaCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;

public record CompraResponse(
        UUID id,
        UUID listaId,
        String nomeLista,
        String categoria,
        String estabelecimento,
        StatusCompra status,
        Instant iniciadaEm,
        ParticipanteCompraReferenciaResponse finalizadaPor,
        Instant finalizadaEm,
        List<ParticipanteCompraResponse> participantes,
        List<ItemCompraResponse> itens,
        ContextoUsuarioCompraResponse contextoUsuario) {

    public static CompraResponse from(ResultadoConsultaCompra resultado, UUID usuarioId) {
        var compra = resultado.compra();
        var mapper = new ItemCompraResponseMapper(resultado, usuarioId);
        var finalizadaPor = compra.getFinalizadaPorParticipanteCompra() == null ? null
                : resultado.participantes().stream()
                        .filter(p -> p.getId().equals(compra.getFinalizadaPorParticipanteCompra().getId()))
                        .findFirst().map(ParticipanteCompraReferenciaResponse::from)
                        .orElseThrow(CompraListaInconsistenteException::new);
        boolean podeFinalizar = resultado.participanteCompra()
                && compra.getStatus() == StatusCompra.EM_ANDAMENTO
                && compra.getListaCompra().getStatus() == StatusListaCompra.EM_COMPRA
                && !resultado.itens().isEmpty()
                && resultado.itens().stream().noneMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA);
        return new CompraResponse(
                compra.getId(),
                compra.getListaCompra().getId(),
                compra.getNomeListaSnapshot(),
                compra.getCategoriaSnapshot(),
                compra.getEstabelecimentoSnapshot(),
                compra.getStatus(),
                compra.getIniciadaEm(),
                finalizadaPor,
                compra.getFinalizadaEm(),
                resultado.participantes().stream().map(participante -> new ParticipanteCompraResponse(
                        participante.getId(),
                        participante.getMembroFamilia().getId(),
                        participante.getMembroFamilia().getUsuario().getId(),
                        participante.getNomeSnapshot(),
                        participante.getPapelSnapshot(),
                        participante.getGeradoEm(),
                        new PresencaOperacionalResponse(participante.getPresencaOperacional(), participante.getPresencaAlteradaEm()))).toList(),
                resultado.itens().stream()
                        .map(mapper::from)
                        .toList(),
                new ContextoUsuarioCompraResponse(resultado.participanteCompra(), resultado.participanteCompra() && compra.getStatus() == StatusCompra.EM_ANDAMENTO, podeFinalizar, compra.getStatus() == StatusCompra.FINALIZADA && compra.getListaCompra().getStatus() == StatusListaCompra.FINALIZADA && compra.getListaCompra().getFamilia().getStatus() == com.mercadeira.api.familia.domain.StatusFamilia.ATIVA && resultado.itens().stream().noneMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA)));
    }

    public record ParticipanteCompraResponse(
            UUID id,
            UUID membroFamiliaId,
            UUID usuarioId,
            String nome,
            PapelMembroFamilia papel,
            Instant geradoEm, PresencaOperacionalResponse presencaOperacional) {
    }

    public record PresencaOperacionalResponse(com.mercadeira.api.compra.domain.PresencaOperacional estado, Instant alteradaEm) {}

    public record ContextoUsuarioCompraResponse(boolean participanteCompra, boolean podeAlterarPresenca, boolean podeFinalizarCompra, boolean podeReutilizarLista) {
    }
}
