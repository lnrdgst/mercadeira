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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarListasFamilia {
    private final ValidadorAcessoListaCompra acesso;
    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final Clock clock;
    public ListarListasFamilia(ValidadorAcessoListaCompra acesso, ListaCompraRepository listaRepository, CompraRepository compraRepository, Clock clock) {
        this.acesso = acesso; this.listaRepository = listaRepository; this.compraRepository = compraRepository; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<ListaCompra> listar(UUID usuarioId, UUID familiaId) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return listaRepository.findPrincipaisByFamiliaId(familiaId, limiteHistorico());
    }

    @Transactional(readOnly = true)
    public Page<Compra> historico(UUID usuarioId, UUID familiaId, int page, int size) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return compraRepository.findHistoricoAnterior(familiaId, limiteHistorico(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
    }

    @Transactional(readOnly = true)
    public long totalHistorico(UUID usuarioId, UUID familiaId) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        return compraRepository.countByListaCompra_Familia_IdAndStatusAndFinalizadaEmBefore(familiaId, StatusCompra.FINALIZADA, limiteHistorico());
    }

    private Instant limiteHistorico() { return Instant.now(clock).minus(14, ChronoUnit.DAYS); }
}
