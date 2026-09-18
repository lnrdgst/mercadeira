package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoPresenca;
import com.mercadeira.api.compra.domain.MotivoCancelamentoPresenca;
import com.mercadeira.api.compra.domain.MotivoResponsabilidade;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.PresencaOperacional;
import com.mercadeira.api.compra.domain.SolicitacaoPresencaCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.compra.repository.SolicitacaoPresencaCompraRepository;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FluxoPresencaCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final SolicitacaoPresencaCompraRepository solicitacoes;
    private final Clock clock;

    public FluxoPresencaCompra(ListaCompraRepository listas, CompraRepository compras,
            MembroFamiliaRepository membros, ParticipanteCompraRepository participantes,
            SolicitacaoPresencaCompraRepository solicitacoes, Clock clock) {
        this.listas = listas;
        this.compras = compras;
        this.membros = membros;
        this.participantes = participantes;
        this.solicitacoes = solicitacoes;
        this.clock = clock;
    }

    @Transactional
    public void solicitarEntrada(UUID usuarioId, UUID familiaId, UUID listaId) {
        var contexto = carregar(usuarioId, familiaId, listaId);
        var compra = contexto.compra();
        var solicitante = contexto.participante();
        if (solicitante.estaPresente()) return;
        var agora = agora();
        var presentes = participantesDaCompra(compra).stream().filter(ParticipanteCompra::estaPresente).toList();
        if (presentes.isEmpty()) {
            solicitante.alterarPresenca(PresencaOperacional.PRESENTE, agora);
            compra.mudarResponsabilidade(solicitante, solicitante, MotivoResponsabilidade.PRIMEIRA_ENTRADA, agora);
            return;
        }
        var responsavel = responsavelAtual(compra, participantesDaCompra(compra));
        if (responsavel == null || !elegivel(responsavel))
            throw new ConflitoPresencaException("A compra possui presenca sem responsavel operacional elegivel.");
        if (solicitacoes.findByCompraIdAndSolicitanteIdAndEstado(compra.getId(), solicitante.getId(), EstadoSolicitacaoPresenca.PENDENTE).isEmpty())
            solicitacoes.save(SolicitacaoPresencaCompra.criar(compra, solicitante, agora));
    }

    @Transactional
    public void rejeitarEntradaLegada(UUID usuarioId, UUID familiaId, UUID listaId) {
        carregar(usuarioId, familiaId, listaId);
        throw new ConflitoPresencaException("Use o fluxo de solicitacao de presenca para entrar na compra.");
    }

    @Transactional
    public void cancelarSolicitacao(UUID usuarioId, UUID familiaId, UUID listaId, UUID solicitacaoId) {
        var contexto = carregar(usuarioId, familiaId, listaId);
        var pedido = pedidoDaCompra(solicitacaoId, contexto.compra());
        if (!pedido.getSolicitanteId().equals(contexto.participante().getId())) throw new AutoridadePresencaException();
        pedido.encerrar(EstadoSolicitacaoPresenca.CANCELADA, contexto.participante().getId(),
                MotivoCancelamentoPresenca.SOLICITANTE, agora());
    }

    @Transactional
    public void decidir(UUID usuarioId, UUID familiaId, UUID listaId, UUID solicitacaoId, boolean aprovar) {
        var contexto = carregar(usuarioId, familiaId, listaId);
        var compra = contexto.compra();
        var decisor = contexto.participante();
        var todos = participantesDaCompra(compra);
        if (!decisor.getId().equals(compra.getResponsavelOperacionalId()) || !elegivel(decisor))
            throw new AutoridadePresencaException();
        var pedido = pedidoDaCompra(solicitacaoId, compra);
        var destino = aprovar ? EstadoSolicitacaoPresenca.APROVADA : EstadoSolicitacaoPresenca.REJEITADA;
        if (pedido.getEstado() != EstadoSolicitacaoPresenca.PENDENTE) {
            if (pedido.getEstado() == destino) return;
            throw new ConflitoPresencaException("A solicitacao ja foi encerrada. Atualize a compra.");
        }
        if (!compra.isCicloOperacionalAtivo() || pedido.getCicloOperacional() != compra.getCicloOperacional())
            throw new ConflitoPresencaException("A solicitacao pertence a um ciclo operacional encerrado.");
        var solicitante = todos.stream().filter(p -> p.getId().equals(pedido.getSolicitanteId())).findFirst()
                .orElseThrow(CompraListaInconsistenteException::new);
        if (solicitante.getMembroFamilia().getStatus() != StatusMembroFamilia.ATIVO || solicitante.estaPresente())
            throw new ConflitoPresencaException("O solicitante nao esta elegivel para receber presenca.");
        var agora = agora();
        if (aprovar) solicitante.alterarPresenca(PresencaOperacional.PRESENTE, agora);
        pedido.encerrar(destino, decisor.getId(), null, agora);
    }

    @Transactional
    public void declararSaida(UUID usuarioId, UUID familiaId, UUID listaId) {
        var contexto = carregar(usuarioId, familiaId, listaId);
        var compra = contexto.compra();
        var participante = contexto.participante();
        var agora = agora();
        participante.alterarPresenca(PresencaOperacional.NAO_PRESENTE, agora);
        if (!participante.getId().equals(compra.getResponsavelOperacionalId())) return;
        var sucessor = participantesDaCompra(compra).stream()
                .filter(p -> !p.getId().equals(participante.getId()))
                .filter(ParticipanteCompra::estaPresente).filter(this::elegivel)
                .min(Comparator.comparing(ParticipanteCompra::getPresencaAlteradaEm)
                        .thenComparing(p -> p.getId().toString())).orElse(null);
        if (sucessor != null) {
            compra.mudarResponsabilidade(sucessor, participante, MotivoResponsabilidade.SUCESSAO, agora);
            return;
        }
        compra.mudarResponsabilidade(null, participante, MotivoResponsabilidade.SEM_PRESENTES, agora);
        cancelarPendentes(compra, MotivoCancelamentoPresenca.SEM_PRESENTES, agora);
    }

    @Transactional
    public void reassumir(UUID usuarioId, UUID familiaId, UUID listaId, long revisao, boolean confirmado) {
        if (!confirmado) throw new IllegalArgumentException("A reassuncao exige confirmacao explicita.");
        var contexto = carregar(usuarioId, familiaId, listaId);
        var compra = contexto.compra();
        var candidato = contexto.participante();
        if (revisao != compra.getResponsabilidadeRevisao())
            throw new ConflitoPresencaException("A responsabilidade operacional mudou. Atualize a compra.");
        var atual = responsavelAtual(compra, participantesDaCompra(compra));
        if (atual == null || !elegivel(atual) || !elegivel(candidato) || candidato.getId().equals(atual.getId()))
            throw new AutoridadePresencaException();
        compra.mudarResponsabilidade(candidato, candidato, MotivoResponsabilidade.REASSUNCAO, agora());
    }

    @Transactional
    public void cancelarPendentesAoFinalizar(Compra compra, Instant instante) {
        cancelarPendentes(compra, MotivoCancelamentoPresenca.COMPRA_FINALIZADA, instante);
    }

    private void cancelarPendentes(Compra compra, MotivoCancelamentoPresenca motivo, Instant instante) {
        for (var pedido : solicitacoes.findByCompraIdAndEstadoOrderBySolicitadaEmAscIdAsc(compra.getId(), EstadoSolicitacaoPresenca.PENDENTE))
            pedido.encerrar(EstadoSolicitacaoPresenca.CANCELADA, null, motivo, instante);
    }

    private Contexto carregar(UUID usuarioId, UUID familiaId, UUID listaId) {
        var lista = listas.findByIdForUpdate(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var compra = compras.findByListaCompra_IdForUpdate(listaId).orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
        MembroFamilia membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        return new Contexto(compra, participante);
    }

    private List<ParticipanteCompra> participantesDaCompra(Compra compra) {
        return participantes.findByCompra_IdOrderByGeradoEmAscIdAsc(compra.getId());
    }
    private ParticipanteCompra responsavelAtual(Compra compra, List<ParticipanteCompra> todos) {
        if (compra.getResponsavelOperacionalId() == null) return null;
        return todos.stream().filter(p -> p.getId().equals(compra.getResponsavelOperacionalId())).findFirst()
                .orElseThrow(CompraListaInconsistenteException::new);
    }
    private SolicitacaoPresencaCompra pedidoDaCompra(UUID id, Compra compra) {
        return solicitacoes.findByIdAndCompraId(id, compra.getId()).orElseThrow(CompraListaInconsistenteException::new);
    }
    private boolean elegivel(ParticipanteCompra participante) {
        return participante.estaPresente() && participante.getMembroFamilia().getStatus() == StatusMembroFamilia.ATIVO;
    }
    private Instant agora() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private record Contexto(Compra compra, ParticipanteCompra participante) {}
}
