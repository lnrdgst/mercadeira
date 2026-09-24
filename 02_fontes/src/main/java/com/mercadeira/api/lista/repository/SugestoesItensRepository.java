package com.mercadeira.api.lista.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SugestoesItensRepository {
    public record Sugestao(String descricao, UnidadeMedida unidadeMedida) {}
    private final NamedParameterJdbcTemplate jdbc;
    public SugestoesItensRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Sugestao> buscar(UUID familiaId, CategoriaCompra categoria, String termo) {
        return jdbc.query("""
            with usos as (
                select i.id, i.descricao, i.unidade_medida, i.atualizado_em as usado_em
                from item_lista i join lista_compra l on l.id = i.lista_compra_id
                where l.familia_id = :familia and l.categoria = :categoria
                  and l.status = 'EM_PREPARACAO' and i.removido_em is null
                union all
                select i.id, i.descricao_snapshot, i.unidade_medida_snapshot,
                       coalesce(i.adicionado_em, c.iniciada_em)
                from item_compra i join compra c on c.id = i.compra_id
                join lista_compra l on l.id = c.lista_compra_id
                where l.familia_id = :familia and l.categoria = :categoria
                  and l.status in ('EM_COMPRA', 'FINALIZADA')
                  and c.status in ('EM_ANDAMENTO', 'FINALIZADA') and i.status <> 'REMOVIDO'
            ), normalizados as (
                select *, btrim(regexp_replace(descricao, '[[:space:]]+', ' ', 'g')) as texto
                from usos
            ), distintos as (
                select *, row_number() over (partition by lower(texto) order by usado_em desc, id desc) as posicao
                from normalizados
                where texto <> '' and strpos(lower(texto), lower(btrim(regexp_replace(:termo, '[[:space:]]+', ' ', 'g')))) > 0
            )
            select texto as descricao, unidade_medida from distintos
            where posicao = 1 order by usado_em desc, lower(texto), id desc limit 10
            """, Map.of("familia", familiaId, "categoria", categoria.name(), "termo", termo),
            (rs, row) -> new Sugestao(rs.getString("descricao"),
                rs.getString("unidade_medida") == null ? null : UnidadeMedida.valueOf(rs.getString("unidade_medida"))));
    }
}
