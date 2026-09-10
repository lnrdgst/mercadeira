package com.mercadeira.api.compra.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.application.ResultadoConsultaCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
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

    public static CompraAtivaResponse from(ResultadoConsultaCompra resultado, UUID usuarioId) {
        var compra = resultado.compra();
        var mapper = new ItemCompraResponseMapper(resultado, usuarioId);
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
                resultado.itens().stream()
                        .map(mapper::from)
                        .toList(),
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

    public record ContextoUsuarioCompraResponse(boolean participanteCompra) {
    }
}
