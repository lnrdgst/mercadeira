package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.lista.domain.ParticipanteLista;

public record ParticipanteListaResponse(UUID membroFamiliaId, UUID usuarioId, String nome,
        PapelMembroFamilia papelFamilia, Instant entrouEm) {
    static ParticipanteListaResponse from(ParticipanteLista participante) {
        return new ParticipanteListaResponse(participante.getMembroFamilia().getId(),
                participante.getMembroFamilia().getUsuario().getId(), participante.getMembroFamilia().getUsuario().getNome(),
                participante.getMembroFamilia().getPapel(), participante.getEntrouEm());
    }
}
