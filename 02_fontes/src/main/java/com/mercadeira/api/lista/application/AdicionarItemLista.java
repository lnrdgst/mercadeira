package com.mercadeira.api.lista.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdicionarItemLista {
    private final ValidadorAcessoListaCompra acesso; private final ItemListaRepository repository; private final Clock clock;
    public AdicionarItemLista(ValidadorAcessoListaCompra acesso, ItemListaRepository repository, Clock clock) { this.acesso = acesso; this.repository = repository; this.clock = clock; }
    @Transactional
    public ItemLista adicionar(UUID executorUsuarioId, UUID familiaId, UUID listaId, String descricao, BigDecimal quantidade,
            UnidadeMedida unidadeMedida, String marca, String observacoes) {
        if (descricao == null || descricao.isBlank()) throw new IllegalArgumentException("A descricao e obrigatoria.");
        ListaCompra lista = acesso.lista(familiaId, listaId); acesso.validarPreparacao(lista);
        MembroFamilia executor = acesso.validarParticipanteAtivo(executorUsuarioId, lista);
        int proximaOrdem = repository.findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(listaId).size() + 1;
        ItemLista item = repository.save(ItemLista.criar(lista, descricao, quantidade, unidadeMedida, marca, observacoes,
                proximaOrdem, executor, clock.instant()));
        lista.registrarAtualizacao(clock.instant());
        return item;
    }
}
