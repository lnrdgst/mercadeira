package com.mercadeira.api.familia.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarFamiliasAtivasUsuario {

    private final MembroFamiliaRepository membroFamiliaRepository;

    public ListarFamiliasAtivasUsuario(MembroFamiliaRepository membroFamiliaRepository) {
        this.membroFamiliaRepository = membroFamiliaRepository;
    }

    @Transactional(readOnly = true)
    public List<MembroFamilia> listar(UUID usuarioId) {
        return membroFamiliaRepository.findByUsuario_IdAndStatusOrderByFamilia_NomeAsc(
                usuarioId, StatusMembroFamilia.ATIVO);
    }
}
