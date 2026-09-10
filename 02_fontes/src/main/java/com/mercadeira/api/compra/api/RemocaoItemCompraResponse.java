package com.mercadeira.api.compra.api;

import java.time.Instant;
import com.mercadeira.api.compra.domain.DecisaoRemocao;

public record RemocaoItemCompraResponse(
        ParticipanteCompraReferenciaResponse solicitadaPor,
        Instant solicitadaEm,
        DecisaoRemocao decisao,
        ParticipanteCompraReferenciaResponse decididaPor,
        Instant decididaEm) {}
