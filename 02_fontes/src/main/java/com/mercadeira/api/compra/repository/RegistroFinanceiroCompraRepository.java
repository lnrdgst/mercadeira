package com.mercadeira.api.compra.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroFinanceiroCompraRepository extends JpaRepository<RegistroFinanceiroCompra, UUID> {

    List<RegistroFinanceiroCompra> findByCompra_IdOrderByCriadoEmAscIdAsc(UUID compraId);

    Optional<RegistroFinanceiroCompra> findByIdAndCompra_Id(UUID id, UUID compraId);

    @Query("""
            select new com.mercadeira.api.compra.repository.ResumoFinanceiroCompraConsulta(
                    registro.compra.id, sum(registro.valor), count(registro.id),
                    count(distinct lower(registro.estabelecimentoNome)), min(registro.estabelecimentoNome))
            from RegistroFinanceiroCompra registro
            where registro.compra.id in :compraIds
            group by registro.compra.id
            """)
    List<ResumoFinanceiroCompraConsulta> resumirPorCompraIds(@Param("compraIds") List<UUID> compraIds);

    @Query("""
            select new com.mercadeira.api.compra.repository.ResumoFinanceiroCompraConsulta(
                    registro.compra.listaCompra.id, sum(registro.valor), count(registro.id),
                    count(distinct lower(registro.estabelecimentoNome)), min(registro.estabelecimentoNome))
            from RegistroFinanceiroCompra registro
            where registro.compra.listaCompra.id in :listaIds
            group by registro.compra.listaCompra.id
            """)
    List<ResumoFinanceiroCompraConsulta> resumirPorListaIds(@Param("listaIds") List<UUID> listaIds);
}
