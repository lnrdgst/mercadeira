package com.mercadeira.api.lista.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarParticipantesLista {
    private final ValidadorAcessoListaCompra acesso; private final ParticipanteListaRepository repository;
    public ListarParticipantesLista(ValidadorAcessoListaCompra acesso, ParticipanteListaRepository repository) { this.acesso = acesso; this.repository = repository; }
    @Transactional(readOnly = true)
    public List<ParticipanteLista> listar(UUID usuarioId, UUID familiaId, UUID listaId) {
        ListaCompra lista = acesso.lista(familiaId, listaId); acesso.validarMembroDaFamilia(usuarioId, lista);
        return repository.findByListaCompra_IdAndSaiuEmIsNull(listaId);
    }
}
