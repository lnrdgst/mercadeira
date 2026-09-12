package com.mercadeira.api.compra.api;

import java.time.Instant;

public record RestauracaoItemCompraResponse(
        ParticipanteCompraReferenciaResponse restauradoPor,
        Instant restauradoEm) {}