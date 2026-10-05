package com.mercadeira.api.compra.api;

import java.time.Instant;

import com.mercadeira.api.compra.application.EstadoAlertaContinuidadeCompra;

public record AlertaContinuidadeCompraResponse(boolean necessario, Instant iniciadaEm, Instant adiadoAte) {
    public static AlertaContinuidadeCompraResponse from(EstadoAlertaContinuidadeCompra estado) {
        return new AlertaContinuidadeCompraResponse(estado.necessario(), estado.iniciadaEm(), estado.adiadoAte());
    }
}
