package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    Optional<Compra> findByListaCompra_Id(UUID listaCompraId);

    List<Compra> findByListaCompra_Familia_IdAndStatusOrderByFinalizadaEmDesc(
            UUID familiaId,
            StatusCompra status);
}
