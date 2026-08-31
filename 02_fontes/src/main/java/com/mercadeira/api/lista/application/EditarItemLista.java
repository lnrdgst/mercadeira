package com.mercadeira.api.lista.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditarItemLista {
    private final ValidadorAcessoListaCompra acesso; private final ItemListaRepository repository; private final Clock clock;
    public EditarItemLista(ValidadorAcessoListaCompra acesso, ItemListaRepository repository, Clock clock) { this.acesso = acesso; this.repository = repository; this.clock = clock; }
    @Transactional
    public ItemLista editar(UUID executorUsuarioId, UUID listaId, UUID itemId, String descricao, BigDecimal quantidade,
            UnidadeMedida unidadeMedida, String marca, String observacoes) {
        if (descricao == null || descricao.isBlank()) throw new IllegalArgumentException("A descricao e obrigatoria.");
        ListaCompra lista = acesso.lista(listaId); acesso.validarPreparacao(lista); acesso.validarParticipanteAtivo(executorUsuarioId, lista);
        ItemLista item = repository.findById(itemId).orElseThrow(() -> new ItemListaNaoEncontradoException(itemId));
        if (!item.getListaCompra().getId().equals(listaId)) throw new ItemListaNaoEncontradoException(itemId);
        if (item.getRemovidoEm() != null) throw new ItemListaJaRemovidoException();
        item.editar(descricao, quantidade, unidadeMedida, marca, observacoes, clock.instant());
        lista.registrarAtualizacao(clock.instant());
        return item;
    }
}
