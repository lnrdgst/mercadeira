package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCompraRepository extends JpaRepository<ItemCompra, UUID> {

    List<ItemCompra> findByCompra_IdOrderByOrdemExibicaoAscIdAsc(UUID compraId);

    List<ItemCompra> findByCompra_IdAndStatusOrderByOrdemExibicaoAscIdAsc(
            UUID compraId,
            StatusItemCompra status);
}
