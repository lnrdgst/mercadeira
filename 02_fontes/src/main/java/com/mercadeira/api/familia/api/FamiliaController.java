package com.mercadeira.api.familia.api;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.familia.application.AprovarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.ConsultarFamiliaAtivaUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.ListarSolicitacoesPendentes;
import com.mercadeira.api.familia.application.MembroSemPermissaoException;
import com.mercadeira.api.familia.application.RejeitarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
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
    private final ConsultarFamiliaAtivaUsuario consultarFamiliaAtivaUsuario;
    private final SolicitarEntradaFamiliaPorCodigo solicitarEntradaFamiliaPorCodigo;
    private final ListarSolicitacoesPendentes listarSolicitacoesPendentes;
    private final AprovarSolicitacaoEntradaFamilia aprovarSolicitacaoEntradaFamilia;
    private final RejeitarSolicitacaoEntradaFamilia rejeitarSolicitacaoEntradaFamilia;

    public FamiliaController(
            UsuarioAutenticado usuarioAutenticado,
            CriarFamilia criarFamilia,
            ConsultarFamiliaAtivaUsuario consultarFamiliaAtivaUsuario,
            SolicitarEntradaFamiliaPorCodigo solicitarEntradaFamiliaPorCodigo,
            ListarSolicitacoesPendentes listarSolicitacoesPendentes,
            AprovarSolicitacaoEntradaFamilia aprovarSolicitacaoEntradaFamilia,
            RejeitarSolicitacaoEntradaFamilia rejeitarSolicitacaoEntradaFamilia) {
        this.usuarioAutenticado = usuarioAutenticado;
        this.criarFamilia = criarFamilia;
        this.consultarFamiliaAtivaUsuario = consultarFamiliaAtivaUsuario;
        this.solicitarEntradaFamiliaPorCodigo = solicitarEntradaFamiliaPorCodigo;
        this.listarSolicitacoesPendentes = listarSolicitacoesPendentes;
        this.aprovarSolicitacaoEntradaFamilia = aprovarSolicitacaoEntradaFamilia;
        this.rejeitarSolicitacaoEntradaFamilia = rejeitarSolicitacaoEntradaFamilia;
    }

    @GetMapping("/ativa")
    public ResponseEntity<FamiliaResponse> consultarAtiva() {
        return consultarFamiliaAtivaUsuario.consultar(usuarioAutenticado.getId())
                .map(this::familiaResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<FamiliaResponse> criar(@Valid @RequestBody CriarFamiliaRequest request) {
        Familia familia = criarFamilia.criar(usuarioAutenticado.getId(), request.nome());
        MembroFamilia membro = membroAtivo();
        return ResponseEntity.status(HttpStatus.CREATED).body(FamiliaResponse.from(familia, membro.getPapel()));
    }

    @PostMapping("/solicitacoes")
    public ResponseEntity<SolicitacaoEntradaFamiliaResponse> solicitarEntrada(
            @Valid @RequestBody SolicitarEntradaFamiliaRequest request) {
        SolicitacaoEntradaFamilia solicitacao = solicitarEntradaFamiliaPorCodigo.solicitar(
                usuarioAutenticado.getId(), request.codigoIngresso());
        return ResponseEntity.status(HttpStatus.CREATED).body(SolicitacaoEntradaFamiliaResponse.from(solicitacao));
    }

    @GetMapping("/solicitacoes")
    public List<SolicitacaoEntradaFamiliaResponse> listarSolicitacoes() {
        MembroFamilia executor = membroAtivo();
        return listarSolicitacoesPendentes.listar(executor.getFamilia().getId(), executor.getId()).stream()
                .map(SolicitacaoEntradaFamiliaResponse::from)
                .toList();
    }

    @PostMapping("/solicitacoes/{solicitacaoId}/aprovar")
    public SolicitacaoEntradaFamiliaResponse aprovar(@PathVariable UUID solicitacaoId) {
        SolicitacaoEntradaFamilia solicitacao = aprovarSolicitacaoEntradaFamilia.aprovar(
                solicitacaoId, membroAtivo().getId());
        return SolicitacaoEntradaFamiliaResponse.from(solicitacao);
    }

    @PostMapping("/solicitacoes/{solicitacaoId}/rejeitar")
    public SolicitacaoEntradaFamiliaResponse rejeitar(@PathVariable UUID solicitacaoId) {
        SolicitacaoEntradaFamilia solicitacao = rejeitarSolicitacaoEntradaFamilia.rejeitar(
                solicitacaoId, membroAtivo().getId());
        return SolicitacaoEntradaFamiliaResponse.from(solicitacao);
    }

    private FamiliaResponse familiaResponse(MembroFamilia membro) {
        return FamiliaResponse.from(membro.getFamilia(), membro.getPapel());
    }

    private MembroFamilia membroAtivo() {
        return consultarFamiliaAtivaUsuario.consultar(usuarioAutenticado.getId())
                .orElseThrow(MembroSemPermissaoException::new);
    }
}
