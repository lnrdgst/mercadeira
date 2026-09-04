package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ParticipanteCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteCompraRepository extends JpaRepository<ParticipanteCompra, UUID> {

    List<ParticipanteCompra> findByCompra_IdOrderByGeradoEmAscIdAsc(UUID compraId);

    Optional<ParticipanteCompra> findByCompra_IdAndMembroFamilia_Id(UUID compraId, UUID membroFamiliaId);

    boolean existsByCompra_IdAndMembroFamilia_Id(UUID compraId, UUID membroFamiliaId);
}
