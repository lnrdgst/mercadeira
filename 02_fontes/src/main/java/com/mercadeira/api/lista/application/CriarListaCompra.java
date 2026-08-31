package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.application.FamiliaInativaException;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarListaCompra {
    private final ValidadorAcessoListaCompra acesso;
    private final ListaCompraRepository listaRepository;
    private final ParticipanteListaRepository participanteRepository;
    private final Clock clock;

    public CriarListaCompra(ValidadorAcessoListaCompra acesso, ListaCompraRepository listaRepository,
            ParticipanteListaRepository participanteRepository, Clock clock) {
        this.acesso = acesso; this.listaRepository = listaRepository;
        this.participanteRepository = participanteRepository; this.clock = clock;
    }

    @Transactional
    public ListaCompra criar(UUID usuarioId, String nome, CategoriaCompra categoria, String estabelecimento) {
        if (nome == null || nome.isBlank() || categoria == null) throw new IllegalArgumentException("Nome e categoria sao obrigatorios.");
        MembroFamilia criador = acesso.membroAtivoDoUsuario(usuarioId);
        if (criador.getFamilia().getStatus() != StatusFamilia.ATIVA) throw new FamiliaInativaException();
        ListaCompra lista = listaRepository.save(ListaCompra.criar(
                criador.getFamilia(), nome, categoria, estabelecimento, criador, clock.instant()));
        participanteRepository.save(ParticipanteLista.criar(lista, criador, clock.instant()));
        return lista;
    }
}
