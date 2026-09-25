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
public class SairDaFamilia {
    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantesCompra;
    private final Clock clock;

    public SairDaFamilia(FamiliaRepository familias, MembroFamiliaRepository membros,
            ParticipanteCompraRepository participantesCompra, Clock clock) {
        this.familias = familias;
        this.membros = membros;
        this.participantesCompra = participantesCompra;
        this.clock = clock;
    }

    @Transactional
    public void sair(UUID familiaId, UUID usuarioId) {
        familias.findByIdForUpdate(familiaId).orElseThrow(SaidaFamiliaInvalidaException::new);
        var vinculo = membros.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .flatMap(membro -> membros.findByIdForUpdate(membro.getId()))
                .orElseThrow(SaidaFamiliaInvalidaException::new);

        if (vinculo.getPapel() == PapelMembroFamilia.ADMINISTRADOR
                && membros.findByFamilia_IdAndStatusAndPapelForUpdate(familiaId, StatusMembroFamilia.ATIVO,
                        PapelMembroFamilia.ADMINISTRADOR).size() == 1) {
            throw new SaidaFamiliaInvalidaException(
                    "Transfira a administração para outro integrante antes de sair da família.");
        }
        if (participantesCompra.existsByCompra_ListaCompra_Familia_IdAndCompra_StatusAndMembroFamilia_Id(
                familiaId, StatusCompra.EM_ANDAMENTO, vinculo.getId())) {
            throw new SaidaFamiliaInvalidaException(
                    "Você participa de uma compra em andamento e não pode sair da família enquanto ela estiver aberta.");
        }
        vinculo.inativar(clock.instant());
    }
}
