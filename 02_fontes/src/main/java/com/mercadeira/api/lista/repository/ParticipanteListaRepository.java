package com.mercadeira.api.lista.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ParticipanteLista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ParticipanteListaRepository extends JpaRepository<ParticipanteLista, UUID> {

    @EntityGraph(attributePaths = { "membroFamilia", "membroFamilia.usuario" })
    List<ParticipanteLista> findByListaCompra_IdAndSaiuEmIsNull(UUID listaCompraId);

    List<ParticipanteLista> findByListaCompra_IdAndSaiuEmIsNullOrderByEntrouEmAscIdAsc(UUID listaCompraId);

    Optional<ParticipanteLista> findByListaCompra_IdAndMembroFamilia_Id(UUID listaCompraId, UUID membroFamiliaId);
}
