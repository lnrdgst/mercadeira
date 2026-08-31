package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReordenarItensLista {
    private final ValidadorAcessoListaCompra acesso; private final ItemListaRepository repository; private final Clock clock;
    public ReordenarItensLista(ValidadorAcessoListaCompra acesso, ItemListaRepository repository, Clock clock) { this.acesso = acesso; this.repository = repository; this.clock = clock; }
    @Transactional
    public void reordenar(UUID executorUsuarioId, UUID listaId, List<UUID> idsNaOrdem) {
        ListaCompra lista = acesso.lista(listaId); acesso.validarPreparacao(lista); acesso.validarParticipanteAtivo(executorUsuarioId, lista);
        List<ItemLista> ativos = repository.findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(listaId);
        if (idsNaOrdem == null || idsNaOrdem.size() != ativos.size() || new HashSet<>(idsNaOrdem).size() != idsNaOrdem.size()
                || !new HashSet<>(idsNaOrdem).equals(ativos.stream().map(ItemLista::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new OrdemItensInvalidaException();
        }
        java.util.Map<UUID, ItemLista> porId = ativos.stream().collect(java.util.stream.Collectors.toMap(ItemLista::getId, item -> item));
        for (int indice = 0; indice < idsNaOrdem.size(); indice++) porId.get(idsNaOrdem.get(indice)).definirOrdemExibicao(indice + 1, clock.instant());
        lista.registrarAtualizacao(clock.instant());
    }
}
