package com.mercadeira.api.lista.application;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ConsultaListasFiltradas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarListasFamilia {
    private final ValidadorAcessoListaCompra acesso;
    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final ConsultaListasFiltradas consulta;
    private final Clock clock;
    public ListarListasFamilia(ValidadorAcessoListaCompra acesso, ListaCompraRepository listaRepository, CompraRepository compraRepository, ConsultaListasFiltradas consulta, Clock clock) {
        this.acesso = acesso; this.listaRepository = listaRepository; this.compraRepository = compraRepository; this.consulta = consulta; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<ListaCompra> listar(UUID usuarioId, UUID familiaId) { return listar(usuarioId, familiaId, new FiltrosListas(null, null, null, null)); }

    @Transactional(readOnly = true)
    public List<ListaCompra> listar(UUID usuarioId, UUID familiaId, FiltrosListas filtros) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return consulta.principais(familiaId, limiteHistorico(), filtros);
    }

    @Transactional(readOnly = true)
    public Page<Compra> historico(UUID usuarioId, UUID familiaId, int page, int size) { return historico(usuarioId, familiaId, page, size, new FiltrosListas(null, null, null, null)); }

    @Transactional(readOnly = true)
    public Page<Compra> historico(UUID usuarioId, UUID familiaId, int page, int size, FiltrosListas filtros) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return consulta.historico(familiaId, limiteHistorico(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)), filtros);
    }

    @Transactional(readOnly = true)
    public long totalHistorico(UUID usuarioId, UUID familiaId) { return totalHistorico(usuarioId, familiaId, new FiltrosListas(null, null, null, null)); }

    @Transactional(readOnly = true)
    public long totalHistorico(UUID usuarioId, UUID familiaId, FiltrosListas filtros) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return consulta.totalHistorico(familiaId, limiteHistorico(), filtros);
    }

    private Instant limiteHistorico() { return Instant.now(clock).minus(14, ChronoUnit.DAYS); }
}
