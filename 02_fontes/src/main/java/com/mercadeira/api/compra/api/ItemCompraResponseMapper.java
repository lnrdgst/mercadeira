package com.mercadeira.api.compra.api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mercadeira.api.compra.application.ResultadoConsultaCompra;
import com.mercadeira.api.compra.application.ResponsavelRemocaoItemCompraInvalidoException;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.DecisaoRemocao;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;

/** Contexto de mapeamento construido uma vez por consulta, sem acesso a repositorios. */
final class ItemCompraResponseMapper {
    private final Map<UUID, ParticipanteCompraReferenciaResponse> porParticipante = new HashMap<>();
    private final Map<UUID, ParticipanteCompraReferenciaResponse> porMembro = new HashMap<>();
    private final boolean emAndamento;
    private final UUID membroAtual;

    ItemCompraResponseMapper(ResultadoConsultaCompra resultado, UUID usuarioId) {
        UUID atual = null;
        for (var participante : resultado.participantes()) {
            var referencia = ParticipanteCompraReferenciaResponse.from(participante);
            porParticipante.put(referencia.participanteCompraId(), referencia);
            porMembro.put(referencia.membroFamiliaId(), referencia);
            if (referencia.usuarioId().equals(usuarioId)) atual = referencia.membroFamiliaId();
        }
        membroAtual = resultado.participanteCompra() ? atual : null;
        emAndamento = resultado.compra().getStatus() == StatusCompra.EM_ANDAMENTO;
    }

    ItemCompraResponse from(ItemCompra item) {
        UUID marcador = item.getMarcadoPorMembroFamilia() == null ? null : item.getMarcadoPorMembroFamilia().getId();
        var remocao = item.getRemocaoSolicitadaEm() == null ? null : new RemocaoItemCompraResponse(
                referenciaRemocao(item.getRemocaoSolicitadaPorMembroFamilia().getId()),
                item.getRemocaoSolicitadaEm(), item.getDecisaoRemocao(),
                item.getRemocaoResolvidaPorMembroFamilia() == null ? null
                        : referenciaRemocao(item.getRemocaoResolvidaPorMembroFamilia().getId()),
                item.getRemocaoResolvidaEm());
        // Apenas o ID da associacao LAZY; a referencia vem dos snapshots ja carregados em lote.
        var restauracao = item.getRestauradoPorParticipanteCompra() == null ? null : new RestauracaoItemCompraResponse(
                porParticipante.get(item.getRestauradoPorParticipanteCompra().getId()), item.getRestauradoEm());
        boolean participanteAtivo = emAndamento && membroAtual != null;
        return new ItemCompraResponse(
                item.getId(), item.getItemListaOrigem() == null ? null : item.getItemListaOrigem().getId(),
                item.isAdicionadoDuranteCompra(), item.getDescricaoSnapshot(), item.getQuantidadeSnapshot(),
                item.getUnidadeMedidaSnapshot(), item.getMarcaSnapshot(), item.getObservacoesSnapshot(),
                item.getOrdemExibicao(), item.getStatus(),
                item.getAdicionadoPorParticipanteCompra() == null ? null
                        : porParticipante.get(item.getAdicionadoPorParticipanteCompra().getId()),
                item.getAdicionadoEm(), porMembro.get(marcador), item.getMarcadoEm(), remocao, restauracao,
                new AcoesItemCompraResponse(
                        participanteAtivo && item.getStatus() == StatusItemCompra.NO_CARRINHO,
                        participanteAtivo && item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA
                                && membroAtual.equals(marcador),
                        participanteAtivo && remocaoAprovadaCoerente(item)));
    }

    private boolean remocaoAprovadaCoerente(ItemCompra item) {
        return item.getStatus() == StatusItemCompra.REMOVIDO
                && item.getDecisaoRemocao() == DecisaoRemocao.APROVADA
                && item.getRemocaoSolicitadaPorMembroFamilia() != null
                && item.getRemocaoSolicitadaEm() != null
                && item.getRemocaoResolvidaPorMembroFamilia() != null
                && item.getRemocaoResolvidaEm() != null
                && !item.getRemocaoResolvidaEm().isBefore(item.getRemocaoSolicitadaEm())
                && item.getMarcadoPorMembroFamilia() != null
                && item.getMarcadoEm() != null
                && item.getMarcadoPorMembroFamilia().getId().equals(item.getRemocaoResolvidaPorMembroFamilia().getId());
    }

    private ParticipanteCompraReferenciaResponse referenciaRemocao(UUID membroId) {
        var referencia = porMembro.get(membroId);
        if (referencia == null) throw new ResponsavelRemocaoItemCompraInvalidoException();
        return referencia;
    }
}
