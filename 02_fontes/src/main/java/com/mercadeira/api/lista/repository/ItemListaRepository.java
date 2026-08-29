package com.mercadeira.api.lista.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ItemLista;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemListaRepository extends JpaRepository<ItemLista, UUID> {

    List<ItemLista> findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(UUID listaCompraId);
}
