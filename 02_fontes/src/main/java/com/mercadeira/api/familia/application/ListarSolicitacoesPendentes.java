package com.mercadeira.api.familia.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarSolicitacoesPendentes {

    private final FamiliaRepository familiaRepository;
    private final SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    private final ValidadorAdministradorFamilia validadorAdministradorFamilia;

    public ListarSolicitacoesPendentes(
            FamiliaRepository familiaRepository,
            SolicitacaoEntradaFamiliaRepository solicitacaoRepository,
            ValidadorAdministradorFamilia validadorAdministradorFamilia) {
        this.familiaRepository = familiaRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.validadorAdministradorFamilia = validadorAdministradorFamilia;
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoEntradaFamilia> listar(UUID familiaId, UUID executorMembroId) {
        Familia familia = familiaRepository.findById(familiaId)
                .orElseThrow(CodigoFamiliaInvalidoException::new);
        validadorAdministradorFamilia.validar(executorMembroId, familia);
        return solicitacaoRepository.findByFamilia_IdAndStatusOrderBySolicitadaEmAsc(
                familiaId, StatusSolicitacaoEntradaFamilia.PENDENTE);
    }
}
