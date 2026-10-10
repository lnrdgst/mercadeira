package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarFamiliasAtivasUsuario {

    private final MembroFamiliaRepository membroFamiliaRepository;
    private final Clock clock;

    public ListarFamiliasAtivasUsuario(MembroFamiliaRepository membroFamiliaRepository, Clock clock) {
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.clock = clock;
    }

    @Transactional
    public List<MembroFamilia> listar(UUID usuarioId) {
        var vinculos = membroFamiliaRepository.findByUsuarioIdForUpdate(usuarioId);
        var familiasAtivas = vinculos.stream()
                .filter(membro -> membro.getStatus() == StatusMembroFamilia.ATIVO)
                .filter(membro -> membro.getFamilia().getStatus() == StatusFamilia.ATIVA)
                .sorted((primeiro, segundo) -> primeiro.getFamilia().getNome()
                        .compareToIgnoreCase(segundo.getFamilia().getNome()))
                .toList();

        boolean existePrincipalValida = familiasAtivas.stream().anyMatch(MembroFamilia::isPrincipal);
        var principaisInvalidas = vinculos.stream()
                .filter(MembroFamilia::isPrincipal)
                .filter(membro -> !familiasAtivas.contains(membro))
                .toList();

        if (!principaisInvalidas.isEmpty()) {
            var agora = clock.instant();
            principaisInvalidas.forEach(membro -> membro.removerComoPrincipal(agora));
            membroFamiliaRepository.flush();
        }

        if (familiasAtivas.size() == 1 && !existePrincipalValida) {
            familiasAtivas.getFirst().tornarPrincipal(clock.instant());
        }

        return familiasAtivas;
    }
}
