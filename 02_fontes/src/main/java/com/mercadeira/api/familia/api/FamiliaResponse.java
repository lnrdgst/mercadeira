package com.mercadeira.api.familia.api;

import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;

public record FamiliaResponse(
        UUID id,
        String nome,
        String codigoIngresso,
        StatusFamilia status,
        PapelMembroFamilia papel) {

    static FamiliaResponse from(Familia familia, PapelMembroFamilia papel) {
        return new FamiliaResponse(
                familia.getId(), familia.getNome(), familia.getCodigoIngresso(), familia.getStatus(), papel);
    }
}
