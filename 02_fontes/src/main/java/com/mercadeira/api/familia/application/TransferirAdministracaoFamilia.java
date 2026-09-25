package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferirAdministracaoFamilia {

    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;
    private final Clock clock;

    public TransferirAdministracaoFamilia(FamiliaRepository familias, MembroFamiliaRepository membros, Clock clock) {
        this.familias = familias;
        this.membros = membros;
        this.clock = clock;
    }

    @Transactional
    public void transferir(UUID familiaId, UUID executorId, UUID membroDestinoId) {
        familias.findByIdForUpdate(familiaId).orElseThrow(TransferenciaAdministracaoInvalidaException::new);

        var administradores = membros.findByFamilia_IdAndStatusAndPapelForUpdate(
                familiaId, StatusMembroFamilia.ATIVO, PapelMembroFamilia.ADMINISTRADOR);
        if (administradores.size() != 1) {
            throw new TransferenciaAdministracaoInvalidaException();
        }

        var administrador = administradores.getFirst();
        if (!administrador.getUsuario().getId().equals(executorId) || administrador.getId().equals(membroDestinoId)) {
            throw new TransferenciaAdministracaoInvalidaException();
        }

        var destino = membros.findByIdForUpdate(membroDestinoId).orElseThrow(TransferenciaAdministracaoInvalidaException::new);
        if (!destino.getFamilia().getId().equals(familiaId)
                || destino.getStatus() != StatusMembroFamilia.ATIVO
                || destino.getPapel() != PapelMembroFamilia.MEMBRO) {
            throw new TransferenciaAdministracaoInvalidaException();
        }

        var agora = clock.instant();
        administrador.rebaixarParaMembro(agora);
        destino.promoverParaAdministrador(agora);
    }
}
