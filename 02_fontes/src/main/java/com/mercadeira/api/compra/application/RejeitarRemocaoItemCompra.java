package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejeitarRemocaoItemCompra {

    private final ContextoRemocaoItemCompra contextoRemocao;
    private final Clock clock;

    public RejeitarRemocaoItemCompra(ContextoRemocaoItemCompra contextoRemocao, Clock clock) {
        this.contextoRemocao = contextoRemocao;
        this.clock = clock;
    }

    @Transactional
    public ResultadoDecisaoRemocaoItemCompra executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId) {
        var contexto = contextoRemocao.carregar(usuarioId, familiaId, listaId, itemCompraId);
        contextoRemocao.validarDecisor(contexto);
        return new ResultadoDecisaoRemocaoItemCompra(
                contexto.item(), contexto.item().rejeitarRemocao(contexto.membro(), clock.instant()));
    }
}
