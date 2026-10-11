package com.mercadeira.api.compra.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.application.ConsultarSolicitacoesItensCompra;
import com.mercadeira.api.compra.application.DecidirSolicitacaoItemCompra;
import com.mercadeira.api.compra.application.SolicitarItemCompraRemoto;
import com.mercadeira.api.compra.application.SolicitarItemCompraRemotoCommand;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoItemCompra;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compras/{compraId}/solicitacoes-itens")
public class SolicitacaoItemCompraController {
    private final UsuarioAutenticado usuario;
    private final SolicitarItemCompraRemoto criar;
    private final ConsultarSolicitacoesItensCompra consultar;
    private final DecidirSolicitacaoItemCompra decidir;

    public SolicitacaoItemCompraController(UsuarioAutenticado usuario, SolicitarItemCompraRemoto criar,
            ConsultarSolicitacoesItensCompra consultar, DecidirSolicitacaoItemCompra decidir) {
        this.usuario = usuario;
        this.criar = criar;
        this.consultar = consultar;
        this.decidir = decidir;
    }

    @PostMapping
    public ResponseEntity<SolicitacaoItemCompraResponse> criar(@PathVariable UUID compraId,
            @Valid @RequestBody SolicitacaoItemCompraRequest request) {
        var resultado = criar.solicitar(usuario.getId(), compraId,
                new SolicitarItemCompraRemotoCommand(request.descricao(), request.quantidade(), request.unidadeMedida()));
        var response = SolicitacaoItemCompraResponse.from(resultado);
        return ResponseEntity.created(URI.create("/api/compras/" + compraId + "/solicitacoes-itens/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<SolicitacaoItemCompraResponse> listar(@PathVariable UUID compraId,
            @RequestParam(required = false) EstadoSolicitacaoItemCompra status) {
        return consultar.consultar(usuario.getId(), compraId, status).stream()
                .map(SolicitacaoItemCompraResponse::from).toList();
    }

    @PostMapping("/{solicitacaoId}/aprovar")
    public SolicitacaoItemCompraResponse aprovar(@PathVariable UUID compraId, @PathVariable UUID solicitacaoId) {
        return SolicitacaoItemCompraResponse.from(decidir.decidir(usuario.getId(), compraId, solicitacaoId, true));
    }

    @PostMapping("/{solicitacaoId}/recusar")
    public SolicitacaoItemCompraResponse recusar(@PathVariable UUID compraId, @PathVariable UUID solicitacaoId) {
        return SolicitacaoItemCompraResponse.from(decidir.decidir(usuario.getId(), compraId, solicitacaoId, false));
    }
}
