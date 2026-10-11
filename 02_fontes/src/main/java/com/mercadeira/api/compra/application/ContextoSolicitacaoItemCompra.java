package com.mercadeira.api.compra.application;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Component;

@Component
public class ContextoSolicitacaoItemCompra {
    private final CompraRepository compras;
    private final ListaCompraRepository listas;
    private final ParticipanteCompraRepository participantes;

    public ContextoSolicitacaoItemCompra(CompraRepository compras, ListaCompraRepository listas,
            ParticipanteCompraRepository participantes) {
        this.compras = compras;
        this.listas = listas;
        this.participantes = participantes;
    }

    // Chamado dentro da transacao do caso de uso. Ordem global: Lista -> Compra -> Solicitacao.
    public Contexto carregar(UUID usuarioId, UUID compraId, boolean bloquear) {
        var listaId = compras.findListaIdById(compraId)
                .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (bloquear) listas.findByIdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        var compra = (bloquear ? compras.findByListaCompra_IdForUpdate(listaId) : compras.findById(compraId))
                .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        var familia = compra.getListaCompra().getFamilia();
        var todos = participantes.findByCompra_IdOrderByGeradoEmAscIdAsc(compraId);
        var atual = todos.stream().filter(p -> p.getMembroFamilia().getUsuario().getId().equals(usuarioId)
                && p.getMembroFamilia().getFamilia().getId().equals(familia.getId())
                && p.getMembroFamilia().getStatus() == StatusMembroFamilia.ATIVO).findFirst()
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        if (familia.getStatus() != StatusFamilia.ATIVA)
            throw new UsuarioNaoParticipaDaCompraException();
        return new Contexto(compra, atual,
                todos.stream().collect(Collectors.toMap(ParticipanteCompra::getId, Function.identity())));
    }

    public record Contexto(Compra compra, ParticipanteCompra participante,
            Map<UUID, ParticipanteCompra> participantes) {}
}
