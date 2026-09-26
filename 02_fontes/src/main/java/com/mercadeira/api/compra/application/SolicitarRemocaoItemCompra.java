package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarRemocaoItemCompra {

    private final ContextoRemocaoItemCompra contextoRemocao;
    private final Clock clock;

    public SolicitarRemocaoItemCompra(ContextoRemocaoItemCompra contextoRemocao, Clock clock) {
        this.contextoRemocao = contextoRemocao;
        this.clock = clock;
    }

    @Transactional
    public ResultadoSolicitarRemocaoItemCompra executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId) {
        var contexto = contextoRemocao.carregar(usuarioId, familiaId, listaId, itemCompraId);
        Instant instante = clock.instant();
        boolean solicitacaoCriada = contexto.item().solicitarRemocao(contexto.membro(), instante);
        boolean removidoAutomaticamente = false;
        if (solicitacaoCriada && contexto.participante().estaPresente() && contexto.item().getMarcadoPorMembroFamilia() != null
                && contexto.item().getMarcadoPorMembroFamilia().getId().equals(contexto.membro().getId())) {
            contextoRemocao.validarDecisor(contexto);
            contexto.item().aprovarRemocao(contexto.membro(), instante);
            removidoAutomaticamente = true;
        }
        return new ResultadoSolicitarRemocaoItemCompra(contexto.item(), solicitacaoCriada, removidoAutomaticamente);
    }
}
