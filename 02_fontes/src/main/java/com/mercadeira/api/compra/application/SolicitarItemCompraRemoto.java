package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import com.mercadeira.api.compra.domain.PresencaOperacional;
import com.mercadeira.api.compra.domain.SolicitacaoItemCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.SolicitacaoItemCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarItemCompraRemoto {
    private final ContextoSolicitacaoItemCompra contextos;
    private final SolicitacaoItemCompraRepository solicitacoes;
    private final Clock clock;

    public SolicitarItemCompraRemoto(ContextoSolicitacaoItemCompra contextos,
            SolicitacaoItemCompraRepository solicitacoes, Clock clock) {
        this.contextos = contextos;
        this.solicitacoes = solicitacoes;
        this.clock = clock;
    }

    @Transactional
    public ResultadoSolicitacaoItemCompra solicitar(UUID usuarioId, UUID compraId, SolicitarItemCompraRemotoCommand dados) {
        var contexto = contextos.carregar(usuarioId, compraId, true);
        if (contexto.compra().getStatus() != StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
        if (contexto.participante().getPresencaOperacional() != PresencaOperacional.NAO_PRESENTE)
            throw new ConflitoPresencaException("Somente participante NAO_PRESENTE pode solicitar item remoto.");
        var pedido = solicitacoes.save(SolicitacaoItemCompra.criar(contexto.compra(), contexto.participante(),
                dados.descricao(), dados.quantidade(), dados.unidadeMedida(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return new ResultadoSolicitacaoItemCompra(pedido, contexto.participantes());
    }
}
