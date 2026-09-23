package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReaproveitarItensForaCompra {
    private final ValidadorAcessoListaCompra acesso;
    private final CriarListaCompra criar;
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final ItemCompraRepository itensCompra;
    private final ItemListaRepository itensLista;
    private final Clock clock;

    public ReaproveitarItensForaCompra(ValidadorAcessoListaCompra acesso, CriarListaCompra criar,
            ListaCompraRepository listas, CompraRepository compras, ItemCompraRepository itensCompra,
            ItemListaRepository itensLista, Clock clock) {
        this.acesso = acesso; this.criar = criar; this.listas = listas; this.compras = compras;
        this.itensCompra = itensCompra; this.itensLista = itensLista; this.clock = clock;
    }

    @Transactional
    public ListaCompra reaproveitar(UUID usuarioId, UUID familiaId, UUID listaId, List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty() || new HashSet<>(itemIds).size() != itemIds.size()) throw new ItensForaCompraInvalidosException();
        var origem = listas.findByIdForUpdate(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!origem.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var executor = acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        var compra = compras.findByListaCompra_Id(listaId).orElseThrow(CompraListaInconsistenteException::new);
        if (origem.getStatus() != StatusListaCompra.FINALIZADA || compra.getStatus() != StatusCompra.FINALIZADA) throw new CompraListaInconsistenteException();
        var porId = itensCompra.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(item -> item.getId(), item -> item));
        var selecionados = itemIds.stream().map(porId::get).toList();
        if (selecionados.stream().anyMatch(item -> item == null || (item.getStatus() != StatusItemCompra.PENDENTE && item.getStatus() != StatusItemCompra.REMOVIDO))) throw new ItensForaCompraInvalidosException();
        var nova = criar.criar(usuarioId, familiaId, compra.getNomeListaSnapshot(),
                CategoriaCompra.valueOf(compra.getCategoriaSnapshot()), compra.getEstabelecimentoSnapshot());
        var agora = clock.instant();
        for (int ordem = 0; ordem < selecionados.size(); ordem++) {
            var item = selecionados.get(ordem);
            itensLista.save(ItemLista.criar(nova, item.getDescricaoSnapshot(), item.getQuantidadeSnapshot(),
                    item.getUnidadeMedidaSnapshot() == null ? null : UnidadeMedida.valueOf(item.getUnidadeMedidaSnapshot()),
                    item.getMarcaSnapshot(), item.getObservacoesSnapshot(), ordem, executor, agora));
        }
        return nova;
    }
}
