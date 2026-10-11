package com.mercadeira.api.compra.application;

import java.util.Map;
import java.util.UUID;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.SolicitacaoItemCompra;

public record ResultadoSolicitacaoItemCompra(SolicitacaoItemCompra solicitacao,
        Map<UUID, ParticipanteCompra> participantes) {}
