package com.mercadeira.api.lista.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarItensLista {
    private final ValidadorAcessoListaCompra acesso; private final ItemListaRepository repository;
    public ListarItensLista(ValidadorAcessoListaCompra acesso, ItemListaRepository repository) { this.acesso = acesso; this.repository = repository; }
    @Transactional(readOnly = true)
    public List<ItemLista> listar(UUID usuarioId, UUID listaId) {
        ListaCompra lista = acesso.lista(listaId); acesso.validarMembroDaFamilia(usuarioId, lista);
        return repository.findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(listaId);
    }
}
