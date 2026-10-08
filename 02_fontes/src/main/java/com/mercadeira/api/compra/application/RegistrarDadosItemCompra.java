package com.mercadeira.api.compra.application;

import java.math.BigDecimal;
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
public class RegistrarDadosItemCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final ItemCompraRepository itens;

    public RegistrarDadosItemCompra(ListaCompraRepository listas, CompraRepository compras,
            MembroFamiliaRepository membros, ParticipanteCompraRepository participantes, ItemCompraRepository itens) {
        this.listas = listas; this.compras = compras; this.membros = membros; this.participantes = participantes; this.itens = itens;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemId,
            BigDecimal precoUnitario, BigDecimal quantidadeComprada) {
        var item = validarEdicao(usuarioId, familiaId, listaId, itemId);
        item.registrarDadosDaCompra(precoUnitario, quantidadeComprada);
    }

    /** Reutilizado por acoes que exigem a mesma capacidade operacional da edicao financeira. */
    @Transactional
    public com.mercadeira.api.compra.domain.ItemCompra validarEdicao(UUID usuarioId, UUID familiaId, UUID listaId,
            UUID itemId) {
        var lista = listas.findById(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var compra = compras.findByListaCompra_IdForUpdate(listaId).orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        if (!participante.estaPresente()) throw new PresencaOperacionalObrigatoriaException();
        var item = itens.findByIdForUpdate(itemId).orElseThrow(() -> new ItemCompraNaoEncontradoException(itemId));
        if (!item.getCompra().getId().equals(compra.getId())) throw new ItemCompraNaoEncontradoException(itemId);
        return item;
    }
}
