package com.mercadeira.api.compra.repository;

import java.util.*;
import com.mercadeira.api.compra.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoPresencaCompraRepository extends JpaRepository<SolicitacaoPresencaCompra, UUID> {
    List<SolicitacaoPresencaCompra> findByCompraIdAndEstadoOrderBySolicitadaEmAscIdAsc(UUID compraId, EstadoSolicitacaoPresenca estado);
    Optional<SolicitacaoPresencaCompra> findByCompraIdAndSolicitanteIdAndEstado(UUID compraId, UUID solicitanteId, EstadoSolicitacaoPresenca estado);
    Optional<SolicitacaoPresencaCompra> findFirstByCompraIdAndSolicitanteIdOrderBySolicitadaEmDescIdDesc(UUID compraId, UUID solicitanteId);
    Optional<SolicitacaoPresencaCompra> findByIdAndCompraId(UUID id, UUID compraId);
}
