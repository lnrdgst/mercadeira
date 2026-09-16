package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColocarItemNoCarrinho {

    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final MembroFamiliaRepository membroRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final ItemCompraRepository itemRepository;
    private final Clock clock;

    public ColocarItemNoCarrinho(ListaCompraRepository listaRepository, CompraRepository compraRepository,
            MembroFamiliaRepository membroRepository, ParticipanteCompraRepository participanteRepository,
            ItemCompraRepository itemRepository, Clock clock) {
        this.listaRepository = listaRepository;
        this.compraRepository = compraRepository;
        this.membroRepository = membroRepository;
        this.participanteRepository = participanteRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoColocarItemNoCarrinho executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId) {
        var lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var compra = compraRepository.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) {
            throw new CompraForaDeAndamentoException();
        }
        var participante = participanteRepository.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        var item = itemRepository.findByIdForUpdate(itemCompraId)
                .orElseThrow(() -> new ItemCompraNaoEncontradoException(itemCompraId));
        if (!item.getCompra().getId().equals(compra.getId())) {
            throw new ItemCompraNaoEncontradoException(itemCompraId);
        }
        if (!participante.estaPresente()) throw new PresencaOperacionalObrigatoriaException();
        boolean alterado = item.colocarNoCarrinho(membro, clock.instant());
        return new ResultadoColocarItemNoCarrinho(item, alterado);
    }
}
