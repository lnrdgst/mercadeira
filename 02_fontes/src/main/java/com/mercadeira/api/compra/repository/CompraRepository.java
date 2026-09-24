package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    Optional<Compra> findByListaCompra_Id(UUID listaCompraId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select compra from Compra compra where compra.listaCompra.id = :listaCompraId")
    Optional<Compra> findByListaCompra_IdForUpdate(@Param("listaCompraId") UUID listaCompraId);

    List<Compra> findByListaCompra_Familia_IdAndStatusOrderByFinalizadaEmDesc(
            UUID familiaId,
            StatusCompra status);

    @EntityGraph(attributePaths = "listaCompra")
    @Query("select compra from Compra compra where compra.listaCompra.familia.id = :familiaId and compra.status = com.mercadeira.api.compra.domain.StatusCompra.FINALIZADA and compra.finalizadaEm < :limite order by compra.finalizadaEm desc")
    Page<Compra> findHistoricoAnterior(@Param("familiaId") UUID familiaId, @Param("limite") Instant limite, Pageable pageable);

    long countByListaCompra_Familia_IdAndStatusAndFinalizadaEmBefore(UUID familiaId, StatusCompra status, Instant limite);
}
