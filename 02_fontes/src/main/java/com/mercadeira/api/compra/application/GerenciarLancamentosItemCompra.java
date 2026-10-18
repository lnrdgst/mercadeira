package com.mercadeira.api.compra.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.LancamentoItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GerenciarLancamentosItemCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final ItemCompraRepository itens;
    private final LancamentoItemCompraRepository lancamentos;
    private final Clock clock;

    public GerenciarLancamentosItemCompra(ListaCompraRepository listas, CompraRepository compras,
            MembroFamiliaRepository membros, ParticipanteCompraRepository participantes, ItemCompraRepository itens,
            LancamentoItemCompraRepository lancamentos, Clock clock) {
        this.listas = listas; this.compras = compras; this.membros = membros; this.participantes = participantes;
        this.itens = itens; this.lancamentos = lancamentos; this.clock = clock;
    }

    @Transactional
    public void adicionar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemId,
            BigDecimal precoUnitario, BigDecimal quantidade) {
        var lancamento = carregarItemAutorizado(usuarioId, familiaId, listaId, itemId)
                .adicionarLancamento(precoUnitario, quantidade, clock.instant());
        lancamentos.save(lancamento);
    }

    @Transactional
    public void editar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemId, UUID lancamentoId,
            BigDecimal precoUnitario, BigDecimal quantidade) {
        carregarItemAutorizado(usuarioId, familiaId, listaId, itemId)
                .editarLancamento(lancamentoId, precoUnitario, quantidade, clock.instant());
    }

    @Transactional
    public void remover(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemId, UUID lancamentoId) {
        ItemCompra item = carregarItemAutorizado(usuarioId, familiaId, listaId, itemId);
        lancamentos.delete(item.removerLancamento(lancamentoId));
    }

    private ItemCompra carregarItemAutorizado(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemId) {
        var lista = listas.findById(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var compra = compras.findByListaCompra_IdForUpdate(listaId).orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        if (!participante.estaPresente()) throw new PresencaOperacionalObrigatoriaException();
        var item = itens.findByIdForUpdate(itemId).orElseThrow(() -> new ItemCompraNaoEncontradoException(itemId));
        if (!item.getCompra().getId().equals(compra.getId())) throw new ItemCompraNaoEncontradoException(itemId);
        return item;
    }
}
