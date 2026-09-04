package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraForaDePreparacaoException;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.application.UsuarioNaoParticipaDaListaException;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IniciarCompra {

    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final MembroFamiliaRepository membroRepository;
    private final ParticipanteListaRepository participanteListaRepository;
    private final ItemListaRepository itemListaRepository;
    private final ParticipanteCompraRepository participanteCompraRepository;
    private final ItemCompraRepository itemCompraRepository;
    private final Clock clock;

    public IniciarCompra(
            ListaCompraRepository listaRepository,
            CompraRepository compraRepository,
            MembroFamiliaRepository membroRepository,
            ParticipanteListaRepository participanteListaRepository,
            ItemListaRepository itemListaRepository,
            ParticipanteCompraRepository participanteCompraRepository,
            ItemCompraRepository itemCompraRepository,
            Clock clock) {
        this.listaRepository = listaRepository;
        this.compraRepository = compraRepository;
        this.membroRepository = membroRepository;
        this.participanteListaRepository = participanteListaRepository;
        this.itemListaRepository = itemListaRepository;
        this.participanteCompraRepository = participanteCompraRepository;
        this.itemCompraRepository = itemCompraRepository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoInicioCompra iniciar(UUID usuarioId, UUID familiaId, UUID listaId) {
        ListaCompra lista = listaRepository.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }

        MembroFamilia iniciador = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);

        Compra compraExistente = compraRepository.findByListaCompra_Id(listaId).orElse(null);
        if (lista.getStatus() == StatusListaCompra.EM_COMPRA) {
            validarParticipacaoAtiva(lista, iniciador);
            if (compraExistente != null && compraExistente.getStatus() == StatusCompra.EM_ANDAMENTO) {
                return new ResultadoInicioCompra(compraExistente, false);
            }
            throw new CompraListaInconsistenteException();
        }
        if (lista.getStatus() != StatusListaCompra.EM_PREPARACAO) {
            throw new ListaCompraForaDePreparacaoException();
        }
        if (compraExistente != null) {
            throw new CompraListaInconsistenteException();
        }

        List<ParticipanteLista> participantes = participanteListaRepository
                .findByListaCompra_IdAndSaiuEmIsNullOrderByEntrouEmAscIdAsc(listaId);
        if (participantes.isEmpty()) {
            throw new ListaCompraSemParticipantesException();
        }
        if (participantes.stream().noneMatch(participante -> participante.getMembroFamilia().getId().equals(iniciador.getId()))) {
            throw new UsuarioNaoParticipaDaListaException();
        }
        List<ItemLista> itens = itemListaRepository
                .findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(listaId);
        if (itens.isEmpty()) {
            throw new ListaCompraSemItensException();
        }

        Instant agora = clock.instant();
        Compra compra = compraRepository.save(Compra.iniciar(lista, iniciador, agora));
        participanteCompraRepository.saveAll(participantes.stream()
                .map(participante -> ParticipanteCompra.criarDaPreparacao(compra, participante, agora))
                .toList());
        itemCompraRepository.saveAll(itens.stream()
                .map(item -> ItemCompra.criarDaPreparacao(compra, item))
                .toList());
        lista.iniciarCompra(agora);

        return new ResultadoInicioCompra(compra, true);
    }

    private void validarParticipacaoAtiva(ListaCompra lista, MembroFamilia membro) {
        ParticipanteLista participante = participanteListaRepository
                .findByListaCompra_IdAndMembroFamilia_Id(lista.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaListaException::new);
        if (participante.getSaiuEm() != null) {
            throw new UsuarioNaoParticipaDaListaException();
        }
    }
}
