package com.mercadeira.api.compra.repository;

import java.util.*;
import com.mercadeira.api.compra.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoItemCompraRepository extends JpaRepository<SolicitacaoItemCompra, UUID> {
    List<SolicitacaoItemCompra> findByCompraIdOrderByCriadoEmAscIdAsc(UUID compraId);
    List<SolicitacaoItemCompra> findByCompraIdAndStatusOrderByCriadoEmAscIdAsc(UUID compraId, EstadoSolicitacaoItemCompra status);
    Optional<SolicitacaoItemCompra> findByIdAndCompraId(UUID id, UUID compraId);
}
