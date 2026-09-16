package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurarItemNoCarrinho {
    private final ContextoRemocaoItemCompra contexto;
    private final Clock clock;

    public RestaurarItemNoCarrinho(ContextoRemocaoItemCompra contexto, Clock clock) {
        this.contexto = contexto;
        this.clock = clock;
    }

    @Transactional
    public ResultadoRestauracaoItemCompra executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId) {
        // Reutiliza validacao de contexto/autorizacao e locks Compra -> Item, sem exigir antigo decisor.
        var carregado = contexto.carregar(usuarioId, familiaId, listaId, itemCompraId);
        if (!carregado.participante().estaPresente()) throw new PresencaOperacionalObrigatoriaException();
        var instante = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean restauradoAgora = carregado.item().restaurarNoCarrinho(carregado.participante(), instante);
        return new ResultadoRestauracaoItemCompra(carregado.item(), restauradoAgora);
    }
}