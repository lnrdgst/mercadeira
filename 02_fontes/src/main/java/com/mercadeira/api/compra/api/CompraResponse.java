package com.mercadeira.api.compra.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.compra.application.ResultadoConsultaCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoPresenca;
import com.mercadeira.api.compra.domain.MotivoCancelamentoPresenca;

public record CompraResponse(
        UUID id,
        UUID listaId,
        String nomeLista,
        String categoria,
        String estabelecimento,
        StatusCompra status,
        Instant iniciadaEm,
        ParticipanteCompraReferenciaResponse finalizadaPor,
        Instant finalizadaEm,
        ResponsabilidadeOperacionalResponse responsabilidadeOperacional,
        SolicitacaoPresencaResponse minhaSolicitacaoPresenca,
        List<SolicitacaoPresencaResponse> solicitacoesPresencaPendentes,
        List<ParticipanteCompraResponse> participantes,
        List<ItemCompraResponse> itens,
        ContextoUsuarioCompraResponse contextoUsuario) {

    public static CompraResponse from(ResultadoConsultaCompra resultado, UUID usuarioId) {
        var compra = resultado.compra();
        var mapper = new ItemCompraResponseMapper(resultado, usuarioId);
        var participanteAtual = resultado.participantes().stream()
                .filter(p -> p.getMembroFamilia().getUsuario().getId().equals(usuarioId)).findFirst().orElse(null);
        var responsavel = compra.getResponsavelOperacionalId() == null ? null : resultado.participantes().stream()
                .filter(p -> p.getId().equals(compra.getResponsavelOperacionalId())).findFirst()
                .orElseThrow(CompraListaInconsistenteException::new);
        var finalizadaPor = compra.getFinalizadaPorParticipanteCompra() == null ? null
                : resultado.participantes().stream()
                        .filter(p -> p.getId().equals(compra.getFinalizadaPorParticipanteCompra().getId()))
                        .findFirst().map(ParticipanteCompraReferenciaResponse::from)
                        .orElseThrow(CompraListaInconsistenteException::new);
        boolean podeFinalizar = resultado.participanteCompra()
                && compra.getStatus() == StatusCompra.EM_ANDAMENTO
                && compra.getListaCompra().getStatus() == StatusListaCompra.EM_COMPRA
                && !resultado.itens().isEmpty()
                && resultado.itens().stream().noneMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA);
        boolean emAndamento = compra.getStatus() == StatusCompra.EM_ANDAMENTO;
        boolean podeSolicitarPresenca = participanteAtual != null && emAndamento && !participanteAtual.estaPresente();
        boolean podeDeclararSaida = participanteAtual != null && emAndamento && participanteAtual.estaPresente();
        boolean podeCancelar = resultado.minhaSolicitacao() != null
                && resultado.minhaSolicitacao().getEstado() == EstadoSolicitacaoPresenca.PENDENTE;
        boolean podeReassumir = emAndamento && participanteAtual != null && participanteAtual.estaPresente()
                && responsavel != null && responsavel.estaPresente()
                && !participanteAtual.getId().equals(responsavel.getId());
        var responsabilidade = new ResponsabilidadeOperacionalResponse(
                responsavel == null ? null : ParticipanteCompraReferenciaResponse.from(responsavel),
                compra.getCicloOperacional(), compra.isCicloOperacionalAtivo(), compra.getResponsabilidadeRevisao(),
                compra.getResponsabilidadeAnteriorId(), compra.getResponsabilidadeAlteradaPorId(),
                compra.getResponsabilidadeAlteradaEm(), compra.getResponsabilidadeMotivo());
        return new CompraResponse(
                compra.getId(),
                compra.getListaCompra().getId(),
                compra.getNomeListaSnapshot(),
                compra.getCategoriaSnapshot(),
                compra.getEstabelecimentoSnapshot(),
                compra.getStatus(),
                compra.getIniciadaEm(),
                finalizadaPor,
                compra.getFinalizadaEm(),
                responsabilidade,
                SolicitacaoPresencaResponse.from(resultado.minhaSolicitacao(), false),
                responsavel != null && participanteAtual != null && responsavel.getId().equals(participanteAtual.getId())
                        ? resultado.solicitacoesPendentes().stream().map(s -> SolicitacaoPresencaResponse.from(s, true)).toList()
                        : List.of(),
                resultado.participantes().stream().map(participante -> new ParticipanteCompraResponse(
                        participante.getId(),
                        participante.getMembroFamilia().getId(),
                        participante.getMembroFamilia().getUsuario().getId(),
                        participante.getNomeSnapshot(),
                        participante.getPapelSnapshot(),
                        participante.getGeradoEm(),
                        new PresencaOperacionalResponse(participante.getPresencaOperacional(), participante.getPresencaAlteradaEm()))).toList(),
                resultado.itens().stream()
                        .map(mapper::from)
                        .toList(),
                new ContextoUsuarioCompraResponse(resultado.participanteCompra(), podeDeclararSaida, podeFinalizar,
                        compra.getStatus() == StatusCompra.FINALIZADA && compra.getListaCompra().getStatus() == StatusListaCompra.FINALIZADA && compra.getListaCompra().getFamilia().getStatus() == com.mercadeira.api.familia.domain.StatusFamilia.ATIVA && resultado.itens().stream().noneMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA),
                        podeSolicitarPresenca, podeCancelar, podeDeclararSaida, podeReassumir));
    }

    public record ParticipanteCompraResponse(
            UUID id,
            UUID membroFamiliaId,
            UUID usuarioId,
            String nome,
            PapelMembroFamilia papel,
            Instant geradoEm, PresencaOperacionalResponse presencaOperacional) {
    }

    public record PresencaOperacionalResponse(com.mercadeira.api.compra.domain.PresencaOperacional estado, Instant alteradaEm) {}

    public record ResponsabilidadeOperacionalResponse(
            ParticipanteCompraReferenciaResponse responsavel,
            long ciclo, boolean cicloAtivo, long revisao, UUID responsavelAnteriorId,
            UUID alteradaPorParticipanteCompraId, Instant alteradaEm,
            com.mercadeira.api.compra.domain.MotivoResponsabilidade motivo) {}

    public record SolicitacaoPresencaResponse(UUID id, UUID solicitanteParticipanteCompraId,
            long ciclo, Instant solicitadaEm, EstadoSolicitacaoPresenca estado,
            UUID encerradaPorParticipanteCompraId, Instant encerradaEm,
            MotivoCancelamentoPresenca motivoCancelamento, AcoesSolicitacaoPresencaResponse acoes) {
        static SolicitacaoPresencaResponse from(com.mercadeira.api.compra.domain.SolicitacaoPresencaCompra solicitacao,
                boolean podeDecidir) {
            if (solicitacao == null) return null;
            return new SolicitacaoPresencaResponse(solicitacao.getId(), solicitacao.getSolicitanteId(),
                    solicitacao.getCicloOperacional(), solicitacao.getSolicitadaEm(), solicitacao.getEstado(),
                    solicitacao.getEncerradaPorId(), solicitacao.getEncerradaEm(), solicitacao.getMotivoCancelamento(),
                    new AcoesSolicitacaoPresencaResponse(podeDecidir
                            && solicitacao.getEstado() == EstadoSolicitacaoPresenca.PENDENTE));
        }
    }
    public record AcoesSolicitacaoPresencaResponse(boolean podeDecidirPresenca) {}

    public record ContextoUsuarioCompraResponse(boolean participanteCompra, boolean podeAlterarPresenca,
            boolean podeFinalizarCompra, boolean podeReutilizarLista, boolean podeSolicitarPresenca,
            boolean podeCancelarSolicitacaoPresenca, boolean podeDeclararSaida,
            boolean podeReassumirResponsabilidade) {
    }
}
