package com.mercadeira.api.lista.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoverParticipanteLista {
    private final ValidadorAcessoListaCompra acesso; private final ParticipanteListaRepository repository; private final Clock clock;
    public RemoverParticipanteLista(ValidadorAcessoListaCompra acesso, ParticipanteListaRepository repository, Clock clock) { this.acesso = acesso; this.repository = repository; this.clock = clock; }
    @Transactional
    public void remover(UUID executorUsuarioId, UUID familiaId, UUID listaId, UUID membroId) {
        ListaCompra lista = acesso.lista(familiaId, listaId); acesso.validarPreparacao(lista); acesso.validarGerenciador(executorUsuarioId, lista);
        if (lista.getCriadaPorMembroFamilia().getId().equals(membroId)) throw new CriadorListaNaoPodeSerRemovidoException();
        ParticipanteLista participante = repository.findByListaCompra_IdAndMembroFamilia_Id(listaId, membroId)
                .filter(p -> p.getSaiuEm() == null).orElseThrow(ParticipanteListaNaoEncontradoException::new);
        participante.sair(clock.instant());
        lista.registrarAtualizacao(clock.instant());
    }
}
