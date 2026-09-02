package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdicionarParticipanteLista {
    private final ValidadorAcessoListaCompra acesso; private final ParticipanteListaRepository repository; private final Clock clock;
    public AdicionarParticipanteLista(ValidadorAcessoListaCompra acesso, ParticipanteListaRepository repository, Clock clock) {
        this.acesso = acesso; this.repository = repository; this.clock = clock;
    }
    @Transactional
    public ParticipanteLista adicionar(UUID executorUsuarioId, UUID familiaId, UUID listaId, UUID membroId) {
        ListaCompra lista = acesso.lista(familiaId, listaId); acesso.validarPreparacao(lista); acesso.validarGerenciador(executorUsuarioId, lista);
        MembroFamilia membro = acesso.membroAtivoDaFamilia(membroId, lista);
        ParticipanteLista participante = repository.findByListaCompra_IdAndMembroFamilia_Id(listaId, membroId)
                .map(atual -> {
                    if (atual.getSaiuEm() != null) atual.reativar(clock.instant());
                    return atual;
                })
                .orElseGet(() -> repository.save(ParticipanteLista.criar(lista, membro, clock.instant())));
        lista.registrarAtualizacao(clock.instant());
        return participante;
    }
}
