package com.mercadeira.api.compra.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record ReassumirResponsabilidadeRequest(long revisao, boolean confirmado) {
    @JsonAnySetter
    public void rejeitarCampoAdicional(String nome, Object valor) {
        throw new IllegalArgumentException("Campo nao permitido: " + nome);
    }
}
