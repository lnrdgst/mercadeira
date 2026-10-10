package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.NomeFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlterarNomeFamilia {

    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;
    private final ValidadorAdministradorFamilia administradores;
    private final Clock clock;

    public AlterarNomeFamilia(FamiliaRepository familias, MembroFamiliaRepository membros,
            ValidadorAdministradorFamilia administradores, Clock clock) {
        this.familias = familias;
        this.membros = membros;
        this.administradores = administradores;
        this.clock = clock;
    }

    @Transactional
    public void alterar(UUID familiaId, UUID executorId, String nome) {
        String nomeNormalizado = NomeFamilia.normalizar(nome);
        var familia = familias.findByIdForUpdate(familiaId).orElseThrow(MembroSemPermissaoException::new);
        if (familia.getStatus() != StatusFamilia.ATIVA) {
            throw new MembroSemPermissaoException();
        }

        var executor = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, executorId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroSemPermissaoException::new);
        administradores.validar(executor.getId(), familia);
        familia.renomear(nomeNormalizado, clock.instant());
    }
}
