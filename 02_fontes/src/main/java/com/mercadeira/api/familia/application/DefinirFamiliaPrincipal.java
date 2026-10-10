package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefinirFamiliaPrincipal {

    private final MembroFamiliaRepository membros;
    private final Clock clock;

    public DefinirFamiliaPrincipal(MembroFamiliaRepository membros, Clock clock) {
        this.membros = membros;
        this.clock = clock;
    }

    @Transactional
    public void definir(UUID usuarioId, UUID familiaId) {
        var vinculos = membros.findByUsuarioIdForUpdate(usuarioId);
        MembroFamilia destino = vinculos.stream()
                .filter(membro -> membro.getFamilia().getId().equals(familiaId))
                .findFirst()
                .orElseThrow(MembroSemPermissaoException::new);

        if (destino.getStatus() != StatusMembroFamilia.ATIVO
                || destino.getFamilia().getStatus() != StatusFamilia.ATIVA) {
            throw new MembroSemPermissaoException();
        }

        var agora = clock.instant();
        vinculos.stream().filter(MembroFamilia::isPrincipal).forEach(membro -> membro.removerComoPrincipal(agora));
        membros.flush();
        destino.tornarPrincipal(agora);
    }
}
