package com.mercadeira.api.compra.application;

import java.util.List;
import java.util.UUID;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoItemCompra;
import com.mercadeira.api.compra.repository.SolicitacaoItemCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarSolicitacoesItensCompra {
    private final ContextoSolicitacaoItemCompra contextos;
    private final SolicitacaoItemCompraRepository solicitacoes;

    public ConsultarSolicitacoesItensCompra(ContextoSolicitacaoItemCompra contextos,
            SolicitacaoItemCompraRepository solicitacoes) {
        this.contextos = contextos;
        this.solicitacoes = solicitacoes;
    }

    @Transactional(readOnly = true)
    public List<ResultadoSolicitacaoItemCompra> consultar(UUID usuarioId, UUID compraId, EstadoSolicitacaoItemCompra status) {
        var contexto = contextos.carregar(usuarioId, compraId, false);
        var pedidos = status == null ? solicitacoes.findByCompraIdOrderByCriadoEmAscIdAsc(compraId)
                : solicitacoes.findByCompraIdAndStatusOrderByCriadoEmAscIdAsc(compraId, status);
        return pedidos.stream().map(s -> new ResultadoSolicitacaoItemCompra(s, contexto.participantes())).toList();
    }
}
