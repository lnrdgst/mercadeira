package com.mercadeira.api.compra.api;

import java.util.UUID;

import com.mercadeira.api.compra.domain.ParticipanteCompra;

public record ParticipanteCompraReferenciaResponse(
        UUID participanteCompraId,
        UUID membroFamiliaId,
        UUID usuarioId,
        String nome) {

    static ParticipanteCompraReferenciaResponse from(ParticipanteCompra participante) {
        return new ParticipanteCompraReferenciaResponse(
                participante.getId(),
                participante.getMembroFamilia().getId(),
                participante.getMembroFamilia().getUsuario().getId(),
                participante.getNomeSnapshot());
    }
}
