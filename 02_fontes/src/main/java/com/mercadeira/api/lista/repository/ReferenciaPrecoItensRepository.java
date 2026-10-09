package com.mercadeira.api.lista.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Busca, em uma unica consulta, a ultima referencia financeira de cada item da lista. */
@Repository
public class ReferenciaPrecoItensRepository {
    public record Referencia(UUID itemListaId, BigDecimal precoUnitario, Instant data, String estabelecimento) { }

    private final NamedParameterJdbcTemplate jdbc;

    public ReferenciaPrecoItensRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Referencia> buscar(UUID familiaId, UUID listaId, String categoria) {
        return jdbc.query("""
                with alvo as (
                    select i.id, btrim(regexp_replace(i.descricao, '[[:space:]]+', ' ', 'g')) as descricao_normalizada,
                           i.unidade_medida
                    from item_lista i
                    where i.lista_compra_id = :listaId and i.removido_em is null
                ), historico as (
                    select a.id as item_lista_id, ic.preco_unitario, c.finalizada_em, c.estabelecimento_snapshot,
                           row_number() over (partition by a.id order by c.finalizada_em desc, ic.id desc) as posicao
                    from alvo a
                    join item_compra ic on lower(btrim(regexp_replace(ic.descricao_snapshot, '[[:space:]]+', ' ', 'g')))
                                         = lower(a.descricao_normalizada)
                    join compra c on c.id = ic.compra_id
                    join lista_compra l on l.id = c.lista_compra_id
                    where l.familia_id = :familiaId
                      and l.categoria = :categoria
                      and c.status = 'FINALIZADA'
                      and c.finalizada_em is not null
                      and ic.status = 'NO_CARRINHO'
                      and ic.preco_unitario > 0
                      and ic.quantidade_comprada > 0
                      and ic.unidade_medida_snapshot is not distinct from a.unidade_medida
                )
                select item_lista_id, preco_unitario, finalizada_em, estabelecimento_snapshot
                from historico where posicao = 1
                """, Map.of("familiaId", familiaId, "listaId", listaId, "categoria", categoria),
                (rs, row) -> new Referencia(UUID.fromString(rs.getString("item_lista_id")),
                        rs.getBigDecimal("preco_unitario"), rs.getTimestamp("finalizada_em").toInstant(),
                        rs.getString("estabelecimento_snapshot")));
    }
}
