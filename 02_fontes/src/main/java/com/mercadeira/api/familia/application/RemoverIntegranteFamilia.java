package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoverIntegranteFamilia {

    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantesCompra;
    private final Clock clock;

    public RemoverIntegranteFamilia(FamiliaRepository familias, MembroFamiliaRepository membros,
            ParticipanteCompraRepository participantesCompra, Clock clock) {
        this.familias = familias;
        this.membros = membros;
        this.participantesCompra = participantesCompra;
        this.clock = clock;
    }

    @Transactional
    public void remover(UUID familiaId, UUID executorId, UUID membroId) {
        familias.findByIdForUpdate(familiaId).orElseThrow(RemocaoIntegranteInvalidaException::new);

        var administradores = membros.findByFamilia_IdAndStatusAndPapel(
                familiaId, StatusMembroFamilia.ATIVO, PapelMembroFamilia.ADMINISTRADOR);
        if (administradores.size() != 1 || !administradores.getFirst().getUsuario().getId().equals(executorId)) {
            throw new RemocaoIntegranteInvalidaException();
        }

        var integrante = membros.findByIdForUpdate(membroId).orElseThrow(RemocaoIntegranteInvalidaException::new);
        if (!integrante.getFamilia().getId().equals(familiaId)
                || integrante.getStatus() != StatusMembroFamilia.ATIVO
                || integrante.getPapel() != PapelMembroFamilia.MEMBRO
                || integrante.getUsuario().getId().equals(executorId)) {
            throw new RemocaoIntegranteInvalidaException();
        }

        if (participantesCompra.existsByCompra_ListaCompra_Familia_IdAndCompra_StatusAndMembroFamilia_Id(
                familiaId, StatusCompra.EM_ANDAMENTO, membroId)) {
            throw new RemocaoIntegranteInvalidaException(
                    "Não é possível remover um integrante participante de uma compra em andamento.");
        }

        integrante.inativar(clock.instant());
    }
}
