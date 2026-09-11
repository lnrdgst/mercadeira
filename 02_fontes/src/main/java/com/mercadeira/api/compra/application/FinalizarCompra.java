package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.FinalizacaoCompraInvalidaException;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalizarCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final ItemCompraRepository itens;
    private final Clock clock;

    public FinalizarCompra(ListaCompraRepository listas, CompraRepository compras, MembroFamiliaRepository membros,
            ParticipanteCompraRepository participantes, ItemCompraRepository itens, Clock clock) {
        this.listas = listas;
        this.compras = compras;
        this.membros = membros;
        this.participantes = participantes;
        this.itens = itens;
        this.clock = clock;
    }

    @Transactional
    public ResultadoFinalizacaoCompra executar(UUID usuarioId, UUID familiaId, UUID listaId) {
        // Ordem global: ListaCompra -> Compra. Todas as mutacoes de itens bloqueiam Compra.
        var lista = listas.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var compra = compras.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId) || !compra.getListaCompra().getId().equals(listaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);

        boolean encerradas = compra.getStatus() == StatusCompra.FINALIZADA
                && lista.getStatus() == StatusListaCompra.FINALIZADA;
        boolean emAndamento = compra.getStatus() == StatusCompra.EM_ANDAMENTO
                && lista.getStatus() == StatusListaCompra.EM_COMPRA;
        if (!encerradas && !emAndamento) {
            throw new CompraListaInconsistenteException();
        }
        var itensCompra = itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId());
        if (itensCompra.isEmpty()) {
            throw new FinalizacaoCompraInvalidaException("A compra sem itens nao pode ser finalizada.");
        }
        if (itensCompra.stream().anyMatch(item -> item.getStatus() == StatusItemCompra.REMOCAO_SOLICITADA)) {
            throw new CompraComRemocaoPendenteException();
        }

        var instante = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        boolean finalizadaAgora = compra.finalizar(participante, instante);
        if (finalizadaAgora) {
            lista.finalizarCompra(instante);
        }
        return new ResultadoFinalizacaoCompra(compra, finalizadaAgora);
    }
}
