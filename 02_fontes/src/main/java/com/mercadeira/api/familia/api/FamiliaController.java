package com.mercadeira.api.familia.api;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.familia.application.AprovarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.ConsultarMinhasSolicitacoesPendentes;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.ListarFamiliasAtivasUsuario;
import com.mercadeira.api.familia.application.ListarSolicitacoesPendentes;
import com.mercadeira.api.familia.application.MembroSemPermissaoException;
import com.mercadeira.api.familia.application.RejeitarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familias")
public class FamiliaController {

    private final UsuarioAutenticado usuarioAutenticado;
    private final CriarFamilia criarFamilia;
    private final ListarFamiliasAtivasUsuario listarFamiliasAtivasUsuario;
    private final ConsultarMinhasSolicitacoesPendentes consultarMinhasSolicitacoesPendentes;
    private final SolicitarEntradaFamiliaPorCodigo solicitarEntradaFamiliaPorCodigo;
    private final ListarSolicitacoesPendentes listarSolicitacoesPendentes;
    private final AprovarSolicitacaoEntradaFamilia aprovarSolicitacaoEntradaFamilia;
    private final RejeitarSolicitacaoEntradaFamilia rejeitarSolicitacaoEntradaFamilia;
    private final MembroFamiliaRepository membroFamiliaRepository;

    public FamiliaController(UsuarioAutenticado usuarioAutenticado, CriarFamilia criarFamilia,
            ListarFamiliasAtivasUsuario listarFamiliasAtivasUsuario,
            ConsultarMinhasSolicitacoesPendentes consultarMinhasSolicitacoesPendentes,
            SolicitarEntradaFamiliaPorCodigo solicitarEntradaFamiliaPorCodigo,
            ListarSolicitacoesPendentes listarSolicitacoesPendentes,
            AprovarSolicitacaoEntradaFamilia aprovarSolicitacaoEntradaFamilia,
            RejeitarSolicitacaoEntradaFamilia rejeitarSolicitacaoEntradaFamilia,
            MembroFamiliaRepository membroFamiliaRepository) {
        this.usuarioAutenticado = usuarioAutenticado;
        this.criarFamilia = criarFamilia;
        this.listarFamiliasAtivasUsuario = listarFamiliasAtivasUsuario;
        this.consultarMinhasSolicitacoesPendentes = consultarMinhasSolicitacoesPendentes;
        this.solicitarEntradaFamiliaPorCodigo = solicitarEntradaFamiliaPorCodigo;
        this.listarSolicitacoesPendentes = listarSolicitacoesPendentes;
        this.aprovarSolicitacaoEntradaFamilia = aprovarSolicitacaoEntradaFamilia;
        this.rejeitarSolicitacaoEntradaFamilia = rejeitarSolicitacaoEntradaFamilia;
        this.membroFamiliaRepository = membroFamiliaRepository;
    }

    @GetMapping
    public List<FamiliaResponse> listarFamilias() {
        return listarFamiliasAtivasUsuario.listar(usuarioAutenticado.getId()).stream()
                .map(this::familiaResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<FamiliaResponse> criar(@Valid @RequestBody CriarFamiliaRequest request) {
        Familia familia = criarFamilia.criar(usuarioAutenticado.getId(), request.nome());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FamiliaResponse.from(familia, PapelMembroFamilia.ADMINISTRADOR));
    }

    @PostMapping("/solicitacoes")
    public ResponseEntity<SolicitacaoEntradaFamiliaResponse> solicitarEntrada(
            @Valid @RequestBody SolicitarEntradaFamiliaRequest request) {
        SolicitacaoEntradaFamilia solicitacao = solicitarEntradaFamiliaPorCodigo.solicitar(
                usuarioAutenticado.getId(), request.codigoIngresso());
        return ResponseEntity.status(HttpStatus.CREATED).body(SolicitacaoEntradaFamiliaResponse.from(solicitacao));
    }

    @GetMapping("/solicitacoes/minhas-pendentes")
    public ResponseEntity<List<MinhaSolicitacaoEntradaPendenteResponse>> minhasPendentes() {
        List<MinhaSolicitacaoEntradaPendenteResponse> pendentes = consultarMinhasSolicitacoesPendentes
                .consultar(usuarioAutenticado.getId()).stream()
                .map(MinhaSolicitacaoEntradaPendenteResponse::from)
                .toList();
        return pendentes.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(pendentes);
    }

    @GetMapping("/{familiaId}/solicitacoes")
    public List<SolicitacaoEntradaFamiliaResponse> listarSolicitacoes(@PathVariable UUID familiaId) {
        MembroFamilia executor = membroAtivoNaFamilia(familiaId);
        return listarSolicitacoesPendentes.listar(familiaId, executor.getId()).stream()
                .map(SolicitacaoEntradaFamiliaResponse::from)
                .toList();
    }

    @GetMapping("/{familiaId}/membros")
    public List<MembroFamiliaResponse> listarMembros(@PathVariable UUID familiaId) {
        membroAtivoNaFamilia(familiaId);
        return membroFamiliaRepository.findByFamilia_IdAndStatusOrderByUsuario_NomeAscIdAsc(familiaId, StatusMembroFamilia.ATIVO)
                .stream().map(MembroFamiliaResponse::from).toList();
    }

    @PostMapping("/{familiaId}/solicitacoes/{solicitacaoId}/aprovar")
    public SolicitacaoEntradaFamiliaResponse aprovar(@PathVariable UUID familiaId, @PathVariable UUID solicitacaoId) {
        SolicitacaoEntradaFamilia solicitacao = aprovarSolicitacaoEntradaFamilia.aprovar(
                familiaId, solicitacaoId, membroAtivoNaFamilia(familiaId).getId());
        return SolicitacaoEntradaFamiliaResponse.from(solicitacao);
    }

    @PostMapping("/{familiaId}/solicitacoes/{solicitacaoId}/rejeitar")
    public SolicitacaoEntradaFamiliaResponse rejeitar(@PathVariable UUID familiaId, @PathVariable UUID solicitacaoId) {
        SolicitacaoEntradaFamilia solicitacao = rejeitarSolicitacaoEntradaFamilia.rejeitar(
                familiaId, solicitacaoId, membroAtivoNaFamilia(familiaId).getId());
        return SolicitacaoEntradaFamiliaResponse.from(solicitacao);
    }

    private FamiliaResponse familiaResponse(MembroFamilia membro) {
        return FamiliaResponse.from(membro.getFamilia(), membro.getPapel());
    }

    private MembroFamilia membroAtivoNaFamilia(UUID familiaId) {
        return membroFamiliaRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioAutenticado.getId(), StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroSemPermissaoException::new);
    }
}
