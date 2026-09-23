package com.mercadeira.api.lista.application;

import java.util.UUID;

import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcluirListaCompra {

    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final ItemListaRepository itens;
    private final ParticipanteListaRepository participantes;
    private final ValidadorAcessoListaCompra acesso;

    public ExcluirListaCompra(
            ListaCompraRepository listas,
            CompraRepository compras,
            ItemListaRepository itens,
            ParticipanteListaRepository participantes,
            ValidadorAcessoListaCompra acesso) {
        this.listas = listas;
        this.compras = compras;
        this.itens = itens;
        this.participantes = participantes;
        this.acesso = acesso;
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID familiaId, UUID listaId) {
        // Mantém a mesma ordem de lock de IniciarCompra: lista, depois compra.
        var lista = listas.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }

        acesso.validarMembroDaFamilia(usuarioId, lista);
        acesso.validarPreparacao(lista);
        acesso.validarGerenciador(usuarioId, lista);

        if (compras.findByListaCompra_Id(listaId).isPresent()) {
            throw new ListaCompraJaUtilizadaException();
        }

        participantes.deleteByListaCompra_Id(listaId);
        itens.deleteByListaCompra_Id(listaId);
        listas.delete(lista);
    }
}
