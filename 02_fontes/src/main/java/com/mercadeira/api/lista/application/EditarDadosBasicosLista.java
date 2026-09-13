package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.UUID;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditarDadosBasicosLista {
    private final ValidadorAcessoListaCompra acesso;
    private final ListaCompraRepository repository;
    private final Clock clock;

    public EditarDadosBasicosLista(ValidadorAcessoListaCompra acesso, ListaCompraRepository repository, Clock clock) {
        this.acesso = acesso;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void editar(UUID usuarioId, UUID familiaId, UUID listaId,
            String nome, CategoriaCompra categoria, String estabelecimento) {
        var lista = repository.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        acesso.validarGerenciador(usuarioId, lista);
        acesso.validarPreparacao(lista);
        lista.editarDadosBasicos(nome, categoria, estabelecimento, clock.instant());
    }
}