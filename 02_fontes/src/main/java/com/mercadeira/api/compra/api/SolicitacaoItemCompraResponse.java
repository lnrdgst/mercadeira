package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.application.ResultadoSolicitacaoItemCompra;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.SolicitacaoItemCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;

public record SolicitacaoItemCompraResponse(UUID id, String descricao, BigDecimal quantidade,
        UnidadeMedida unidadeMedida, EstadoSolicitacaoItemCompra status, Instant criadoEm,
        UUID solicitanteParticipanteCompraId, ReferenciaParticipante solicitante,
        Instant decididoEm, ReferenciaParticipante decisor) {

    public static SolicitacaoItemCompraResponse from(ResultadoSolicitacaoItemCompra resultado) {
        return from(resultado.solicitacao(), resultado.participantes());
    }

    public static SolicitacaoItemCompraResponse from(SolicitacaoItemCompra s, Map<UUID, ParticipanteCompra> participantes) {
        return new SolicitacaoItemCompraResponse(s.getId(), s.getDescricao(), s.getQuantidade(),
                s.getUnidadeMedida(), s.getStatus(), s.getCriadoEm(), s.getSolicitanteId(),
                referencia(s.getSolicitanteId(), participantes), s.getDecididoEm(),
                referencia(s.getDecididoPorId(), participantes));
    }

    private static ReferenciaParticipante referencia(UUID id, Map<UUID, ParticipanteCompra> participantes) {
        if (id == null) return null;
        var participante = participantes.get(id);
        if (participante == null) throw new CompraListaInconsistenteException();
        return new ReferenciaParticipante(id, participante.getNomeSnapshot());
    }

    public record ReferenciaParticipante(UUID id, String nome) {}
}
