package com.mercadeira.api.compra.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarCompraDaLista {

    private final ListaCompraRepository listaRepository;
    private final MembroFamiliaRepository membroRepository;
    private final CompraRepository compraRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final ItemCompraRepository itemRepository;

    public ConsultarCompraDaLista(ListaCompraRepository listaRepository, MembroFamiliaRepository membroRepository,
            CompraRepository compraRepository, ParticipanteCompraRepository participanteRepository,
            ItemCompraRepository itemRepository) {
        this.listaRepository = listaRepository;
        this.membroRepository = membroRepository;
        this.compraRepository = compraRepository;
        this.participanteRepository = participanteRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public ResultadoConsultaCompra consultar(UUID usuarioId, UUID familiaId, UUID listaId) {
        ListaCompra lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        MembroFamilia membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        Compra compra = compraRepository.findByListaCompra_Id(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        List<ParticipanteCompra> participantes = participanteRepository.findByCompra_IdOrderByGeradoEmAscIdAsc(compra.getId());
        List<ItemCompra> itens = itemRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId());
        boolean participanteCompra = participanteRepository.existsByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId());
        return new ResultadoConsultaCompra(compra, participantes, itens, participanteCompra);
    }
}
