package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.SolicitacaoItemCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecidirSolicitacaoItemCompra {
    private final ContextoSolicitacaoItemCompra contextos;
    private final SolicitacaoItemCompraRepository solicitacoes;
    private final ItemCompraRepository itens;
    private final Clock clock;

    public DecidirSolicitacaoItemCompra(ContextoSolicitacaoItemCompra contextos,
            SolicitacaoItemCompraRepository solicitacoes, ItemCompraRepository itens, Clock clock) {
        this.contextos = contextos;
        this.solicitacoes = solicitacoes;
        this.itens = itens;
        this.clock = clock;
    }

    @Transactional
    public ResultadoSolicitacaoItemCompra decidir(UUID usuarioId, UUID compraId, UUID solicitacaoId, boolean aprovar) {
        var contexto = contextos.carregar(usuarioId, compraId, true);
        var compra = contexto.compra();
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
        if (!AutoridadeOperacionalCompra.responsavelAtualElegivel(compra, contexto.participante()))
            throw new AutoridadePresencaException();
        var pedido = solicitacoes.findByIdAndCompraIdForUpdate(solicitacaoId, compraId)
                .orElseThrow(SolicitacaoItemCompraNaoEncontradaException::new);
        var agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (aprovar) {
            pedido.aprovar(contexto.participante(), agora);
            // Autor da inclusao efetiva e o responsavel que aprovou; solicitante fica preservado no pedido.
            itens.save(ItemCompra.criarDuranteCompra(compra, contexto.participante(), pedido.getDescricao(),
                    pedido.getQuantidade(), pedido.getUnidadeMedida(), null, null,
                    itens.findMaiorOrdemExibicaoByCompra_Id(compraId) + 1, agora));
        } else {
            pedido.recusar(contexto.participante(), agora);
        }
        return new ResultadoSolicitacaoItemCompra(pedido, contexto.participantes());
    }
}
