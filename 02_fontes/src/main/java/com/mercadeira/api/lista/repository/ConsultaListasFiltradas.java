package com.mercadeira.api.lista.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.lista.application.FiltrosListas;
import com.mercadeira.api.lista.domain.ListaCompra;

@Repository
public class ConsultaListasFiltradas {
    private final EntityManager em;
    public ConsultaListasFiltradas(EntityManager em) { this.em = em; }
    public List<ListaCompra> principais(UUID familiaId, Instant limite, FiltrosListas filtros) {
        var p = predicados("lista", filtros, false);
        var q = em.createQuery("select lista from ListaCompra lista join fetch lista.criadaPorMembroFamilia criador join fetch criador.usuario where lista.familia.id = :familia and " + p.sql + " and (lista.status <> com.mercadeira.api.lista.domain.StatusListaCompra.FINALIZADA or exists (select compra from Compra compra where compra.listaCompra = lista and compra.status = com.mercadeira.api.compra.domain.StatusCompra.FINALIZADA and compra.finalizadaEm >= :limite)) order by case when lista.status = com.mercadeira.api.lista.domain.StatusListaCompra.EM_COMPRA then 0 when lista.status = com.mercadeira.api.lista.domain.StatusListaCompra.EM_PREPARACAO then 1 else 2 end, lista.atualizadaEm desc", ListaCompra.class);
        q.setParameter("familia", familiaId); q.setParameter("limite", limite); p.aplicar(q); return q.getResultList();
    }
    public Page<Compra> historico(UUID familiaId, Instant limite, Pageable pageable, FiltrosListas filtros) {
        var p = predicados("compra.listaCompra", filtros, true);
        var base = " from Compra compra where compra.listaCompra.familia.id = :familia and compra.status = com.mercadeira.api.compra.domain.StatusCompra.FINALIZADA and compra.finalizadaEm < :limite and " + p.sql;
        var q = em.createQuery("select compra from Compra compra join fetch compra.listaCompra lista join fetch lista.criadaPorMembroFamilia criador join fetch criador.usuario where compra.listaCompra.familia.id = :familia and compra.status = com.mercadeira.api.compra.domain.StatusCompra.FINALIZADA and compra.finalizadaEm < :limite and " + p.sql + " order by compra.finalizadaEm desc", Compra.class);
        q.setParameter("familia", familiaId); q.setParameter("limite", limite); p.aplicar(q); q.setFirstResult((int) pageable.getOffset()); q.setMaxResults(pageable.getPageSize());
        var count = em.createQuery("select count(compra)" + base, Long.class); count.setParameter("familia", familiaId); count.setParameter("limite", limite); p.aplicar(count);
        return new PageImpl<>(q.getResultList(), pageable, count.getSingleResult());
    }
    public long totalHistorico(UUID familiaId, Instant limite, FiltrosListas filtros) { return historico(familiaId, limite, Pageable.ofSize(1), filtros).getTotalElements(); }
    private Predicados predicados(String lista, FiltrosListas f, boolean compra) {
        var partes = new ArrayList<String>(); var valores = new ArrayList<Object>();
        if (f.inicio() != null) { partes.add(lista + ".criadaEm >= ?" + (valores.size()+1)); valores.add(f.inicio()); }
        if (f.fimExclusivo() != null) { partes.add(lista + ".criadaEm < ?" + (valores.size()+1)); valores.add(f.fimExclusivo()); }
        if (f.criadaPorUsuarioId() != null) { partes.add(lista + ".criadaPorMembroFamilia.usuario.id = ?" + (valores.size()+1)); valores.add(f.criadaPorUsuarioId()); }
        if (f.participanteMembroFamiliaId() != null) {
            var parametro = "?" + (valores.size()+1);
            var participanteCompra = "exists (select participante from ParticipanteCompra participante where participante.compra" + (compra ? " = compra" : ".listaCompra = lista") + " and participante.membroFamilia.id = " + parametro + ")";
            var participanteLista = "exists (select participante from ParticipanteLista participante where participante.listaCompra = lista and participante.saiuEm is null and participante.membroFamilia.id = " + parametro + ")";
            partes.add(compra ? participanteCompra : "((lista.status = com.mercadeira.api.lista.domain.StatusListaCompra.EM_PREPARACAO and " + participanteLista + ") or (lista.status <> com.mercadeira.api.lista.domain.StatusListaCompra.EM_PREPARACAO and " + participanteCompra + "))");
            valores.add(f.participanteMembroFamiliaId());
        }
        return new Predicados(partes.isEmpty() ? "1 = 1" : String.join(" and ", partes), valores);
    }
    private record Predicados(String sql, List<Object> valores) { void aplicar(jakarta.persistence.Query q) { for (int i=0;i<valores.size();i++) q.setParameter(i+1,valores.get(i)); } }
}
