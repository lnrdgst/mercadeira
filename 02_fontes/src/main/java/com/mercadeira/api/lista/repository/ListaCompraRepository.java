package com.mercadeira.api.lista.repository;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;

public interface ListaCompraRepository extends JpaRepository<ListaCompra, UUID> {

    List<ListaCompra> findByFamilia_IdOrderByAtualizadaEmDesc(UUID familiaId);

    List<ListaCompra> findByFamilia_IdAndStatusOrderByAtualizadaEmDesc(UUID familiaId, StatusListaCompra status);

    @EntityGraph(attributePaths = { "criadaPorMembroFamilia", "criadaPorMembroFamilia.usuario" })
    @Query("select lista from ListaCompra lista where lista.familia.id = :familiaId and (lista.status <> com.mercadeira.api.lista.domain.StatusListaCompra.FINALIZADA or exists (select compra from Compra compra where compra.listaCompra = lista and compra.status = com.mercadeira.api.compra.domain.StatusCompra.FINALIZADA and compra.finalizadaEm >= :limite)) order by case when lista.status = com.mercadeira.api.lista.domain.StatusListaCompra.EM_COMPRA then 0 when lista.status = com.mercadeira.api.lista.domain.StatusListaCompra.EM_PREPARACAO then 1 else 2 end, lista.atualizadaEm desc")
    List<ListaCompra> findPrincipaisByFamiliaId(@Param("familiaId") UUID familiaId, @Param("limite") Instant limite);

    @EntityGraph(attributePaths = { "familia", "criadaPorMembroFamilia", "criadaPorMembroFamilia.usuario" })
    Optional<ListaCompra> findDetalheById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lista from ListaCompra lista where lista.id = :listaId")
    Optional<ListaCompra> findByIdForUpdate(@Param("listaId") UUID listaId);
}
