package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ItemCompraRepository extends JpaRepository<ItemCompra, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ItemCompra item where item.id = :itemCompraId")
    Optional<ItemCompra> findByIdForUpdate(@Param("itemCompraId") UUID itemCompraId);

    @EntityGraph(attributePaths = {
            "itemListaOrigem",
            "adicionadoPorParticipanteCompra",
            "adicionadoPorParticipanteCompra.membroFamilia",
            "adicionadoPorParticipanteCompra.membroFamilia.usuario",
            "marcadoPorMembroFamilia",
            "remocaoSolicitadaPorMembroFamilia",
            "remocaoResolvidaPorMembroFamilia"
    })
    List<ItemCompra> findByCompra_IdOrderByOrdemExibicaoAscIdAsc(UUID compraId);

    List<ItemCompra> findByCompra_IdAndStatusOrderByOrdemExibicaoAscIdAsc(
            UUID compraId,
            StatusItemCompra status);

    @Query("select coalesce(max(item.ordemExibicao), 0) from ItemCompra item where item.compra.id = :compraId")
    Integer findMaiorOrdemExibicaoByCompra_Id(@Param("compraId") UUID compraId);
}
