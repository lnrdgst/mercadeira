package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.LancamentoItemCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LancamentoItemCompraRepository extends JpaRepository<LancamentoItemCompra, UUID> {

    List<LancamentoItemCompra> findByItemCompra_IdOrderByCriadoEmAscIdAsc(UUID itemCompraId);
}
