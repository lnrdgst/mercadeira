package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ParticipanteCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteCompraRepository extends JpaRepository<ParticipanteCompra, UUID> {

    List<ParticipanteCompra> findByCompra_Id(UUID compraId);
}
