package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroFinanceiroCompraRepository extends JpaRepository<RegistroFinanceiroCompra, UUID> {

    List<RegistroFinanceiroCompra> findByCompra_IdOrderByCriadoEmAscIdAsc(UUID compraId);

    Optional<RegistroFinanceiroCompra> findByIdAndCompra_Id(UUID id, UUID compraId);
}
