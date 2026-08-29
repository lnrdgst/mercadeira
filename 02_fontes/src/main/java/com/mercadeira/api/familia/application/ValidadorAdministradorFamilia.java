package com.mercadeira.api.familia.application;

import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Component;

@Component
public class ValidadorAdministradorFamilia {

    private final MembroFamiliaRepository membroFamiliaRepository;

    public ValidadorAdministradorFamilia(MembroFamiliaRepository membroFamiliaRepository) {
        this.membroFamiliaRepository = membroFamiliaRepository;
    }

    public MembroFamilia validar(UUID membroId, Familia familia) {
        MembroFamilia membro = membroFamiliaRepository.findById(membroId)
                .orElseThrow(MembroSemPermissaoException::new);

        if (!membro.getFamilia().getId().equals(familia.getId())
                || membro.getStatus() != StatusMembroFamilia.ATIVO
                || membro.getPapel() != PapelMembroFamilia.ADMINISTRADOR) {
            throw new MembroSemPermissaoException();
        }
        return membro;
    }
}
