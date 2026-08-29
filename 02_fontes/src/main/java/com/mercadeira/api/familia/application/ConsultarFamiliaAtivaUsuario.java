package com.mercadeira.api.familia.application;

import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarFamiliaAtivaUsuario {

    private final MembroFamiliaRepository membroFamiliaRepository;

    public ConsultarFamiliaAtivaUsuario(MembroFamiliaRepository membroFamiliaRepository) {
        this.membroFamiliaRepository = membroFamiliaRepository;
    }

    @Transactional(readOnly = true)
    public Optional<MembroFamilia> consultar(UUID usuarioId) {
        return membroFamiliaRepository.findByUsuario_IdAndStatus(usuarioId, StatusMembroFamilia.ATIVO);
    }
}
