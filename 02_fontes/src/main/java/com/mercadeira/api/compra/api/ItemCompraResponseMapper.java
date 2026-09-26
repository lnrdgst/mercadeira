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
    private final Map<UUID, Boolean> presencaPorMembro = new HashMap<>();
    private final Map<UUID, Boolean> naoInformadaPorMembro = new HashMap<>();
    private final boolean emAndamento;
    private final UUID membroAtual;
    private final boolean presente;
    private final UUID responsavelOperacionalId;

    ItemCompraResponseMapper(ResultadoConsultaCompra resultado, UUID usuarioId) {
        UUID atual = null;
        boolean presencaAtual = false;
        for (var participante : resultado.participantes()) {
            var referencia = ParticipanteCompraReferenciaResponse.from(participante);
            porParticipante.put(referencia.participanteCompraId(), referencia);
            porMembro.put(referencia.membroFamiliaId(), referencia);
            presencaPorMembro.put(referencia.membroFamiliaId(), participante.estaPresente());
            naoInformadaPorMembro.put(referencia.membroFamiliaId(), participante.getPresencaOperacional() == com.mercadeira.api.compra.domain.PresencaOperacional.NAO_INFORMADA);
            if (referencia.usuarioId().equals(usuarioId)) {
                atual = referencia.membroFamiliaId();
                presencaAtual = participante.estaPresente();
            }
        }
        membroAtual = resultado.participanteCompra() ? atual : null;
        presente = resultado.participanteCompra() && presencaAtual;
        emAndamento = resultado.compra().getStatus() == StatusCompra.EM_ANDAMENTO;
        responsavelOperacionalId = resultado.compra().getResponsavelOperacionalId();
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
        boolean naoInformada = membroAtual != null && Boolean.TRUE.equals(naoInformadaPorMembro.get(membroAtual));
        boolean podeOperar = participanteAtivo && !naoInformada;
        boolean originalPresente = marcador != null && presente(marcador);
        boolean fallback = !originalPresente && presenteAtual() && responsavelOperacionalId != null
                && membroAtual != null && responsavelOperacionalId.equals(porMembro.get(membroAtual).participanteCompraId());
        return new ItemCompraResponse(
                item.getId(), item.getItemListaOrigem() == null ? null : item.getItemListaOrigem().getId(),
                item.isAdicionadoDuranteCompra(), item.getDescricaoSnapshot(), item.getQuantidadeSnapshot(),
                item.getUnidadeMedidaSnapshot(), item.getMarcaSnapshot(), item.getObservacoesSnapshot(),
                item.getOrdemExibicao(), item.getStatus(),
                item.getAdicionadoPorParticipanteCompra() == null ? null
                        : porParticipante.get(item.getAdicionadoPorParticipanteCompra().getId()),
                item.getAdicionadoEm(), porMembro.get(marcador), item.getMarcadoEm(), remocao, restauracao,
                new AcoesItemCompraResponse(
                        participanteAtivo && presente && item.getStatus() == StatusItemCompra.PENDENTE,
                        podeOperar && item.getStatus() == StatusItemCompra.NO_CARRINHO,
                        participanteAtivo && presente && item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA
                                && (membroAtual.equals(marcador) && originalPresente || fallback),
                        participanteAtivo && presente && remocaoAprovadaCoerente(item)));
    }

    private boolean presente(UUID membroId) {
        return Boolean.TRUE.equals(presencaPorMembro.get(membroId));
    }

    private boolean presenteAtual() { return presente; }

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
