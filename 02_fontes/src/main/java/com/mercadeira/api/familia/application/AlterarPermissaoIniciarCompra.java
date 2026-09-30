package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlterarPermissaoIniciarCompra {
    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;
    private final ValidadorAdministradorFamilia administradores;
    private final Clock clock;

    public AlterarPermissaoIniciarCompra(FamiliaRepository familias, MembroFamiliaRepository membros,
            ValidadorAdministradorFamilia administradores, Clock clock) {
        this.familias = familias; this.membros = membros; this.administradores = administradores; this.clock = clock;
    }

    @Transactional
    public void alterar(UUID familiaId, UUID executorId, UUID membroId, boolean podeIniciarCompra) {
        var familia = familias.findByIdForUpdate(familiaId).orElseThrow(MembroSemPermissaoException::new);
        var executor = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, executorId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroSemPermissaoException::new);
        administradores.validar(executor.getId(), familia);
        var membro = membros.findByIdForUpdate(membroId).orElseThrow(MembroSemPermissaoException::new);
        if (!membro.getFamilia().getId().equals(familiaId) || membro.getStatus() != StatusMembroFamilia.ATIVO) throw new MembroSemPermissaoException();
        membro.alterarPodeIniciarCompra(podeIniciarCompra, clock.instant());
    }
}
