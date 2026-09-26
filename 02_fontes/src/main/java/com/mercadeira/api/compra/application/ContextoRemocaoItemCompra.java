package com.mercadeira.api.compra.application;

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
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Component;

@Component
class ContextoRemocaoItemCompra {

    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final MembroFamiliaRepository membroRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final ItemCompraRepository itemRepository;

    ContextoRemocaoItemCompra(ListaCompraRepository listaRepository, CompraRepository compraRepository,
            MembroFamiliaRepository membroRepository, ParticipanteCompraRepository participanteRepository,
            ItemCompraRepository itemRepository) {
        this.listaRepository = listaRepository;
        this.compraRepository = compraRepository;
        this.membroRepository = membroRepository;
        this.participanteRepository = participanteRepository;
        this.itemRepository = itemRepository;
    }

    Contexto carregar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId) {
        var lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        MembroFamilia membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(com.mercadeira.api.lista.application.MembroFamiliaInvalidoException::new);
        Compra compra = compraRepository.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) {
            throw new CompraForaDeAndamentoException();
        }
        ParticipanteCompra participante = participanteRepository.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        ItemCompra item = itemRepository.findByIdForUpdate(itemCompraId)
                .orElseThrow(() -> new ItemCompraNaoEncontradoException(itemCompraId));
        if (!item.getCompra().getId().equals(compra.getId())) {
            throw new ItemCompraNaoEncontradoException(itemCompraId);
        }
        if (participante.getPresencaOperacional() == com.mercadeira.api.compra.domain.PresencaOperacional.NAO_INFORMADA)
            throw new PresencaOperacionalObrigatoriaException();
        return new Contexto(compra, item, membro, participante);
    }

    void validarDecisor(Contexto contexto) {
        MembroFamilia responsavelOriginal = contexto.item().getMarcadoPorMembroFamilia();
        if (responsavelOriginal == null || !participanteRepository.existsByCompra_IdAndMembroFamilia_Id(
                contexto.compra().getId(), responsavelOriginal.getId())) {
            throw new ResponsavelRemocaoItemCompraInvalidoException();
        }
        var participanteOriginal = participanteRepository.findByCompra_IdAndMembroFamilia_Id(contexto.compra().getId(), responsavelOriginal.getId())
                .orElseThrow(ResponsavelRemocaoItemCompraInvalidoException::new);
        boolean originalPresente = participanteOriginal.estaPresente();
        boolean decisorPrincipal = originalPresente && responsavelOriginal.getId().equals(contexto.membro().getId());
        boolean fallbackResponsavel = !originalPresente && contexto.compra().getResponsavelOperacionalId() != null
                && contexto.compra().getResponsavelOperacionalId().equals(contexto.participante().getId())
                && contexto.participante().estaPresente();
        if (!decisorPrincipal && !fallbackResponsavel) {
            throw new UsuarioNaoPodeDecidirRemocaoItemCompraException();
        }
    }

    record Contexto(Compra compra, ItemCompra item, MembroFamilia membro, ParticipanteCompra participante) {
    }
}
