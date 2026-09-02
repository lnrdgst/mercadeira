package com.mercadeira.api.lista.application;

import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import org.springframework.stereotype.Component;

@Component
class ValidadorAcessoListaCompra {

    private final MembroFamiliaRepository membroRepository;
    private final ListaCompraRepository listaRepository;
    private final ParticipanteListaRepository participanteRepository;

    ValidadorAcessoListaCompra(MembroFamiliaRepository membroRepository, ListaCompraRepository listaRepository,
            ParticipanteListaRepository participanteRepository) {
        this.membroRepository = membroRepository;
        this.listaRepository = listaRepository;
        this.participanteRepository = participanteRepository;
    }

    MembroFamilia membroAtivoNaFamilia(UUID usuarioId, UUID familiaId) {
        return membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
    }

    ListaCompra lista(UUID listaId) {
        return listaRepository.findById(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
    }

    ListaCompra lista(UUID familiaId, UUID listaId) {
        ListaCompra lista = lista(listaId);
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        return lista;
    }

    MembroFamilia validarMembroDaFamilia(UUID usuarioId, ListaCompra lista) {
        return membroAtivoNaFamilia(usuarioId, lista.getFamilia().getId());
    }

    void validarPreparacao(ListaCompra lista) {
        if (lista.getStatus() != StatusListaCompra.EM_PREPARACAO) {
            throw new ListaCompraForaDePreparacaoException();
        }
    }

    MembroFamilia validarGerenciador(UUID usuarioId, ListaCompra lista) {
        MembroFamilia executor = validarMembroDaFamilia(usuarioId, lista);
        if (executor.getId().equals(lista.getCriadaPorMembroFamilia().getId())
                || executor.getPapel() == PapelMembroFamilia.ADMINISTRADOR) {
            return executor;
        }
        throw new MembroFamiliaInvalidoException();
    }

    MembroFamilia validarParticipanteAtivo(UUID usuarioId, ListaCompra lista) {
        MembroFamilia membro = validarMembroDaFamilia(usuarioId, lista);
        ParticipanteLista participante = participanteRepository.findByListaCompra_IdAndMembroFamilia_Id(lista.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaListaException::new);
        if (participante.getSaiuEm() != null) {
            throw new UsuarioNaoParticipaDaListaException();
        }
        return membro;
    }

    MembroFamilia membroAtivoDaFamilia(UUID membroId, ListaCompra lista) {
        MembroFamilia membro = membroRepository.findById(membroId).orElseThrow(MembroFamiliaInvalidoException::new);
        if (membro.getStatus() != StatusMembroFamilia.ATIVO
                || !membro.getFamilia().getId().equals(lista.getFamilia().getId())) {
            throw new MembroFamiliaInvalidoException();
        }
        return membro;
    }
}
