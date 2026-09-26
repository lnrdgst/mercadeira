package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.ItemCompra;
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
public class AdicionarItemDuranteCompra {

    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final MembroFamiliaRepository membroRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final ItemCompraRepository itemRepository;
    private final Clock clock;

    public AdicionarItemDuranteCompra(
            ListaCompraRepository listaRepository,
            CompraRepository compraRepository,
            MembroFamiliaRepository membroRepository,
            ParticipanteCompraRepository participanteRepository,
            ItemCompraRepository itemRepository,
            Clock clock) {
        this.listaRepository = listaRepository;
        this.compraRepository = compraRepository;
        this.membroRepository = membroRepository;
        this.participanteRepository = participanteRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional
    public ItemCompra executar(
            UUID usuarioId,
            UUID familiaId,
            UUID listaId,
            AdicionarItemDuranteCompraCommand command) {
        if (command.descricao() == null || command.descricao().isBlank()) {
            throw new IllegalArgumentException("A descricao e obrigatoria.");
        }

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
        if (participante.getPresencaOperacional() == com.mercadeira.api.compra.domain.PresencaOperacional.NAO_INFORMADA)
            throw new PresencaOperacionalObrigatoriaException();

        int proximaOrdem = itemRepository.findMaiorOrdemExibicaoByCompra_Id(compra.getId()) + 1;
        ItemCompra item = ItemCompra.criarDuranteCompra(
                compra,
                participante,
                command.descricao(),
                command.quantidade(),
                command.unidadeMedida(),
                command.marca(),
                command.observacoes(),
                proximaOrdem,
                clock.instant());
        return itemRepository.save(item);
    }
}
