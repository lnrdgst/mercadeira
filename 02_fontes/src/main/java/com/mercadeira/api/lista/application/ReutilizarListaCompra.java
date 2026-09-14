package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.UUID;
import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.lista.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReutilizarListaCompra {
    private final ValidadorAcessoListaCompra acesso;
    private final CriarListaCompra criar;
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final ItemCompraRepository itensCompra;
    private final ItemListaRepository itensLista;
    private final Clock clock;
    public ReutilizarListaCompra(ValidadorAcessoListaCompra acesso, CriarListaCompra criar,
            ListaCompraRepository listas, CompraRepository compras, ItemCompraRepository itensCompra,
            ItemListaRepository itensLista, Clock clock) {
        this.acesso = acesso; this.criar = criar; this.listas = listas; this.compras = compras;
        this.itensCompra = itensCompra; this.itensLista = itensLista; this.clock = clock;
    }
    @Transactional
    public ListaCompra reutilizar(UUID usuarioId, UUID familiaId, UUID listaId) {
        var origem = listas.findByIdForUpdate(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!origem.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var executor = acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        var compra = compras.findByListaCompra_Id(listaId).orElseThrow(CompraListaInconsistenteException::new);
        if (origem.getStatus() != StatusListaCompra.FINALIZADA || compra.getStatus() != StatusCompra.FINALIZADA)
            throw new CompraListaInconsistenteException();
        var itens = itensCompra.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId());
        if (itens.stream().anyMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA))
            throw new CompraListaInconsistenteException();
        var nova = criar.criar(usuarioId, familiaId, compra.getNomeListaSnapshot(),
                CategoriaCompra.valueOf(compra.getCategoriaSnapshot()), compra.getEstabelecimentoSnapshot());
        int ordem = 0;
        var agora = clock.instant();
        for (var item : itens) {
            if (item.getStatus() != StatusItemCompra.NO_CARRINHO && item.getStatus() != StatusItemCompra.PENDENTE) continue;
            itensLista.save(ItemLista.criar(nova, item.getDescricaoSnapshot(), item.getQuantidadeSnapshot(),
                    item.getUnidadeMedidaSnapshot() == null ? null : UnidadeMedida.valueOf(item.getUnidadeMedidaSnapshot()),
                    item.getMarcaSnapshot(), item.getObservacoesSnapshot(), ordem++, executor, agora));
        }
        return nova;
    }
}