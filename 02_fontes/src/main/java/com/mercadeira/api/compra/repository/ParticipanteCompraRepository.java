package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteCompraRepository extends JpaRepository<ParticipanteCompra, UUID> {

    @EntityGraph(attributePaths = { "membroFamilia", "membroFamilia.usuario" })
    List<ParticipanteCompra> findByCompra_IdOrderByGeradoEmAscIdAsc(UUID compraId);

    Optional<ParticipanteCompra> findByCompra_IdAndMembroFamilia_Id(UUID compraId, UUID membroFamiliaId);

    boolean existsByCompra_IdAndMembroFamilia_Id(UUID compraId, UUID membroFamiliaId);

    boolean existsByCompra_ListaCompra_Familia_IdAndCompra_StatusAndMembroFamilia_Id(
            UUID familiaId, StatusCompra status, UUID membroFamiliaId);
}
