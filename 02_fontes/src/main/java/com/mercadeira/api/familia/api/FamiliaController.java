package com.mercadeira.api.familia.api;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.application.AprovarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.ConsultarMinhasSolicitacoesPendentes;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.ListarFamiliasAtivasUsuario;
import com.mercadeira.api.familia.application.ListarSolicitacoesPendentes;
import com.mercadeira.api.familia.application.MembroSemPermissaoException;
import com.mercadeira.api.familia.application.RejeitarSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.application.RemoverIntegranteFamilia;
import com.mercadeira.api.familia.application.SairDaFamilia;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.application.TransferirAdministracaoFamilia;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

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
    private final TransferirAdministracaoFamilia transferirAdministracaoFamilia;
    private final RemoverIntegranteFamilia removerIntegranteFamilia;
    private final SairDaFamilia sairDaFamilia;
    private final MembroFamiliaRepository membroFamiliaRepository;
    private final ParticipanteCompraRepository participanteCompraRepository;

    public FamiliaController(UsuarioAutenticado usuarioAutenticado, CriarFamilia criarFamilia,
            ListarFamiliasAtivasUsuario listarFamiliasAtivasUsuario,
            ConsultarMinhasSolicitacoesPendentes consultarMinhasSolicitacoesPendentes,
            SolicitarEntradaFamiliaPorCodigo solicitarEntradaFamiliaPorCodigo,
            ListarSolicitacoesPendentes listarSolicitacoesPendentes,
            AprovarSolicitacaoEntradaFamilia aprovarSolicitacaoEntradaFamilia,
            RejeitarSolicitacaoEntradaFamilia rejeitarSolicitacaoEntradaFamilia,
            TransferirAdministracaoFamilia transferirAdministracaoFamilia,
            RemoverIntegranteFamilia removerIntegranteFamilia,
            SairDaFamilia sairDaFamilia,
            MembroFamiliaRepository membroFamiliaRepository,
            ParticipanteCompraRepository participanteCompraRepository) {
        this.usuarioAutenticado = usuarioAutenticado;
        this.criarFamilia = criarFamilia;
        this.listarFamiliasAtivasUsuario = listarFamiliasAtivasUsuario;
        this.consultarMinhasSolicitacoesPendentes = consultarMinhasSolicitacoesPendentes;
        this.solicitarEntradaFamiliaPorCodigo = solicitarEntradaFamiliaPorCodigo;
        this.listarSolicitacoesPendentes = listarSolicitacoesPendentes;
        this.aprovarSolicitacaoEntradaFamilia = aprovarSolicitacaoEntradaFamilia;
        this.rejeitarSolicitacaoEntradaFamilia = rejeitarSolicitacaoEntradaFamilia;
        this.transferirAdministracaoFamilia = transferirAdministracaoFamilia;
        this.removerIntegranteFamilia = removerIntegranteFamilia;
        this.sairDaFamilia = sairDaFamilia;
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.participanteCompraRepository = participanteCompraRepository;
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
                .body(FamiliaResponse.from(familia, PapelMembroFamilia.ADMINISTRADOR, false,
                        MotivoSaidaFamiliaIndisponivel.ADMINISTRADOR_UNICO));
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
    @Transactional(readOnly = true)
    public List<SolicitacaoEntradaFamiliaResponse> listarSolicitacoes(@PathVariable UUID familiaId) {
        MembroFamilia executor = membroAtivoNaFamilia(familiaId);
        return listarSolicitacoesPendentes.listar(familiaId, executor.getId()).stream()
                .map(SolicitacaoEntradaFamiliaResponse::from)
                .toList();
    }

    @GetMapping("/{familiaId}/membros")
    @Transactional(readOnly = true)
    public List<MembroFamiliaResponse> listarMembros(@PathVariable UUID familiaId) {
        membroAtivoNaFamilia(familiaId);
        var membros = membroFamiliaRepository.findByFamilia_IdAndStatusOrderByUsuario_NomeAscIdAsc(familiaId, StatusMembroFamilia.ATIVO);
        var administradores = membros.stream().filter(membro -> membro.getPapel() == PapelMembroFamilia.ADMINISTRADOR).toList();
        boolean executorEhAdministradorUnico = administradores.size() == 1
                && administradores.getFirst().getUsuario().getId().equals(usuarioAutenticado.getId());
        return membros.stream().map(membro -> {
            boolean elegivel = executorEhAdministradorUnico && membro.getPapel() == PapelMembroFamilia.MEMBRO && !membro.getUsuario().getId().equals(usuarioAutenticado.getId());
            boolean bloqueadoPorCompra = elegivel && participanteCompraRepository.existsByCompra_ListaCompra_Familia_IdAndCompra_StatusAndMembroFamilia_Id(familiaId, StatusCompra.EM_ANDAMENTO, membro.getId());
            return MembroFamiliaResponse.from(membro, usuarioAutenticado.getId(),
                executorEhAdministradorUnico && membro.getPapel() == PapelMembroFamilia.MEMBRO,
                elegivel && !bloqueadoPorCompra, bloqueadoPorCompra ? MotivoRemocaoIndisponivel.COMPRA_EM_ANDAMENTO : null);
        }).toList();
    }

    @PostMapping("/{familiaId}/membros/{membroId}/transferir-administracao")
    public ResponseEntity<Void> transferirAdministracao(@PathVariable UUID familiaId, @PathVariable UUID membroId) {
        transferirAdministracaoFamilia.transferir(familiaId, usuarioAutenticado.getId(), membroId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{familiaId}/membros/{membroId}")
    public ResponseEntity<Void> removerIntegrante(@PathVariable UUID familiaId, @PathVariable UUID membroId) {
        removerIntegranteFamilia.remover(familiaId, usuarioAutenticado.getId(), membroId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{familiaId}/membros/me")
    public ResponseEntity<Void> sair(@PathVariable UUID familiaId) {
        sairDaFamilia.sair(familiaId, usuarioAutenticado.getId());
        return ResponseEntity.noContent().build();
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
        var administradores = membroFamiliaRepository.findByFamilia_IdAndStatusAndPapel(
                membro.getFamilia().getId(), StatusMembroFamilia.ATIVO, PapelMembroFamilia.ADMINISTRADOR);
        MotivoSaidaFamiliaIndisponivel motivo = null;
        if (membro.getPapel() == PapelMembroFamilia.ADMINISTRADOR && administradores.size() == 1) {
            motivo = MotivoSaidaFamiliaIndisponivel.ADMINISTRADOR_UNICO;
        } else if (participanteCompraRepository.existsByCompra_ListaCompra_Familia_IdAndCompra_StatusAndMembroFamilia_Id(
                membro.getFamilia().getId(), StatusCompra.EM_ANDAMENTO, membro.getId())) {
            motivo = MotivoSaidaFamiliaIndisponivel.COMPRA_EM_ANDAMENTO;
        }
        return FamiliaResponse.from(membro.getFamilia(), membro.getPapel(), motivo == null, motivo);
    }

    private MembroFamilia membroAtivoNaFamilia(UUID familiaId) {
        return membroFamiliaRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioAutenticado.getId(), StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroSemPermissaoException::new);
    }
}
