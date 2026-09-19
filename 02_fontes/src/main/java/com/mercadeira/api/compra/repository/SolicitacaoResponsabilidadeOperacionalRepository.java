package com.mercadeira.api.compra.repository;

import java.util.*;
import com.mercadeira.api.compra.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoResponsabilidadeOperacionalRepository extends JpaRepository<SolicitacaoResponsabilidadeOperacional, UUID> {
    Optional<SolicitacaoResponsabilidadeOperacional> findByIdAndCompraId(UUID id, UUID compraId);
    Optional<SolicitacaoResponsabilidadeOperacional> findFirstByCompraIdAndSolicitanteIdOrderBySolicitadaEmDescIdDesc(UUID compraId, UUID solicitanteId);
    Optional<SolicitacaoResponsabilidadeOperacional> findByCompraIdAndSolicitanteIdAndEstado(UUID compraId, UUID solicitanteId, EstadoSolicitacaoResponsabilidade estado);
    List<SolicitacaoResponsabilidadeOperacional> findByCompraIdAndEstadoOrderBySolicitadaEmAscIdAsc(UUID compraId, EstadoSolicitacaoResponsabilidade estado);
}
