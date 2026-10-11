package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoItemCompra;
import com.mercadeira.api.compra.domain.SolicitacaoItemCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SolicitacaoItemCompraRepository extends JpaRepository<SolicitacaoItemCompra, UUID> {
    List<SolicitacaoItemCompra> findByCompraIdOrderByCriadoEmAscIdAsc(UUID compraId);
    List<SolicitacaoItemCompra> findByCompraIdAndStatusOrderByCriadoEmAscIdAsc(UUID compraId, EstadoSolicitacaoItemCompra status);
    Optional<SolicitacaoItemCompra> findByIdAndCompraId(UUID id, UUID compraId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitacaoItemCompra s where s.id = :id and s.compraId = :compraId")
    Optional<SolicitacaoItemCompra> findByIdAndCompraIdForUpdate(@Param("id") UUID id, @Param("compraId") UUID compraId);
}
