package com.mercadeira.api.compra.application;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;

public final class AutoridadeOperacionalCompra {
    private AutoridadeOperacionalCompra() {}

    public static boolean responsavelAtualElegivel(Compra compra, ParticipanteCompra participante) {
        return participante != null && participante.getId().equals(compra.getResponsavelOperacionalId())
                && participante.estaPresente()
                && participante.getMembroFamilia().getStatus() == StatusMembroFamilia.ATIVO;
    }
}
