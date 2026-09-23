package com.mercadeira.api.familia.api;

import java.util.UUID;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;

public record MembroFamiliaResponse(UUID membroFamiliaId, UUID usuarioId, String nome, String email,
        PapelMembroFamilia papel, boolean usuarioAtual, Acoes acoes) {

    public record Acoes(boolean podeTransferirAdministracao) {
    }

    static MembroFamiliaResponse from(MembroFamilia membro, UUID usuarioAtualId, boolean podeTransferirAdministracao) {
        return new MembroFamiliaResponse(membro.getId(), membro.getUsuario().getId(), membro.getUsuario().getNome(),
                membro.getUsuario().getEmail(), membro.getPapel(), membro.getUsuario().getId().equals(usuarioAtualId),
                new Acoes(podeTransferirAdministracao));
    }
}
