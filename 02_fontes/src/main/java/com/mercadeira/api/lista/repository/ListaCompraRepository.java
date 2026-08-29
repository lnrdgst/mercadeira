package com.mercadeira.api.lista.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListaCompraRepository extends JpaRepository<ListaCompra, UUID> {

    List<ListaCompra> findByFamilia_IdOrderByAtualizadaEmDesc(UUID familiaId);

    List<ListaCompra> findByFamilia_IdAndStatusOrderByAtualizadaEmDesc(UUID familiaId, StatusListaCompra status);
}
