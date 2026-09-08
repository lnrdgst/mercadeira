package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    Optional<Compra> findByListaCompra_Id(UUID listaCompraId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select compra from Compra compra where compra.listaCompra.id = :listaCompraId")
    Optional<Compra> findByListaCompra_IdForUpdate(@Param("listaCompraId") UUID listaCompraId);

    List<Compra> findByListaCompra_Familia_IdAndStatusOrderByFinalizadaEmDesc(
            UUID familiaId,
            StatusCompra status);
}
