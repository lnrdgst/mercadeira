package com.mercadeira.api.compra.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mercadeira.api.compra.domain.PresencaOperacional;

public record AlterarMinhaPresencaRequest(PresencaOperacional estado) {
    public AlterarMinhaPresencaRequest {
        if (estado == null || estado == PresencaOperacional.NAO_INFORMADA) {
            throw new IllegalArgumentException("Declare PRESENTE ou NAO_PRESENTE.");
        }
    }

    @JsonAnySetter
    public void rejeitarCampoAdicional(String nome, Object valor) {
        throw new IllegalArgumentException("Campo nao permitido: " + nome);
    }
}
