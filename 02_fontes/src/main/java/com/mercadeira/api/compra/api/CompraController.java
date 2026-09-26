package com.mercadeira.api.compra.api;

import java.net.URI;
import java.util.UUID;

import com.mercadeira.api.compra.application.FinalizarCompra;
import com.mercadeira.api.compra.application.RestaurarItemNoCarrinho;
import com.mercadeira.api.compra.application.SolicitarRemocaoItemCompra;
import com.mercadeira.api.compra.application.AprovarRemocaoItemCompra;
import com.mercadeira.api.compra.application.RejeitarRemocaoItemCompra;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.application.ConsultarCompraDaLista;
import com.mercadeira.api.compra.application.AdicionarItemDuranteCompra;
import com.mercadeira.api.compra.application.AdicionarItemDuranteCompraCommand;
import com.mercadeira.api.compra.application.ColocarItemNoCarrinho;
import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.compra.application.FluxoPresencaCompra;
import com.mercadeira.api.compra.application.ConflitoPresencaException;
import com.mercadeira.api.compra.domain.PresencaOperacional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familias/{familiaId}/listas/{listaId}/compra")
public class CompraController {

    private final SolicitarRemocaoItemCompra solicitarRemocao;
    private final AprovarRemocaoItemCompra aprovarRemocao;
    private final RejeitarRemocaoItemCompra rejeitarRemocao;
    private final FinalizarCompra finalizarCompra;
    private final RestaurarItemNoCarrinho restaurarItemNoCarrinho;
    private final com.mercadeira.api.compra.application.AlterarMinhaPresencaCompra alterarPresenca;
    private final FluxoPresencaCompra fluxoPresenca;
    private final UsuarioAutenticado usuario;
    private final IniciarCompra iniciarCompra;
    private final ConsultarCompraDaLista consultarCompra;
    private final ColocarItemNoCarrinho colocarItemNoCarrinho;
    private final AdicionarItemDuranteCompra adicionarItemDuranteCompra;

    public CompraController(UsuarioAutenticado usuario, IniciarCompra iniciarCompra,
            ConsultarCompraDaLista consultarCompra, ColocarItemNoCarrinho colocarItemNoCarrinho,
            AdicionarItemDuranteCompra adicionarItemDuranteCompra, SolicitarRemocaoItemCompra solicitarRemocao,
            AprovarRemocaoItemCompra aprovarRemocao, RejeitarRemocaoItemCompra rejeitarRemocao, FinalizarCompra finalizarCompra,
            RestaurarItemNoCarrinho restaurarItemNoCarrinho,
            com.mercadeira.api.compra.application.AlterarMinhaPresencaCompra alterarPresenca,
            FluxoPresencaCompra fluxoPresenca) {
        this.alterarPresenca = alterarPresenca;
        this.fluxoPresenca = fluxoPresenca;
        this.solicitarRemocao = solicitarRemocao;
        this.aprovarRemocao = aprovarRemocao;
        this.rejeitarRemocao = rejeitarRemocao;
        this.finalizarCompra = finalizarCompra;
        this.restaurarItemNoCarrinho = restaurarItemNoCarrinho;
        this.usuario = usuario;
        this.iniciarCompra = iniciarCompra;
        this.consultarCompra = consultarCompra;
        this.colocarItemNoCarrinho = colocarItemNoCarrinho;
        this.adicionarItemDuranteCompra = adicionarItemDuranteCompra;
    }

    @PostMapping
    public ResponseEntity<CompraResponse> iniciar(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) IniciarCompraRequest request) {
        var resultado = iniciarCompra.iniciar(usuario.getId(), familiaId, listaId,
                request == null || request.participantesPresentesIds() == null ? java.util.Set.of() : new java.util.HashSet<>(request.participantesPresentesIds()));
        var response = CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
        if (!resultado.criada()) {
            return ResponseEntity.ok(response);
        }
        URI location = URI.create("/api/familias/" + familiaId + "/listas/" + listaId + "/compra");
        return ResponseEntity.created(location).body(response);
    }

    @org.springframework.web.bind.annotation.PutMapping("/minha-presenca")
    public CompraResponse alterarMinhaPresenca(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @Valid @RequestBody AlterarMinhaPresencaRequest request) {
        if (request.estado() == PresencaOperacional.PRESENTE)
            fluxoPresenca.rejeitarEntradaLegada(usuario.getId(), familiaId, listaId);
        fluxoPresenca.declararSaida(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/minha-presenca/solicitacoes")
    public CompraResponse solicitarPresenca(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        fluxoPresenca.solicitarEntrada(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/minha-presenca/solicitacoes/{solicitacaoId}/cancelar")
    public CompraResponse cancelarSolicitacaoPresenca(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID solicitacaoId) {
        fluxoPresenca.cancelarSolicitacao(usuario.getId(), familiaId, listaId, solicitacaoId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/solicitacoes-presenca/{solicitacaoId}/aprovar")
    public CompraResponse aprovarSolicitacaoPresenca(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID solicitacaoId) {
        fluxoPresenca.decidir(usuario.getId(), familiaId, listaId, solicitacaoId, true);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/solicitacoes-presenca/{solicitacaoId}/rejeitar")
    public CompraResponse rejeitarSolicitacaoPresenca(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID solicitacaoId) {
        fluxoPresenca.decidir(usuario.getId(), familiaId, listaId, solicitacaoId, false);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/responsabilidade-operacional/reassumir")
    public CompraResponse reassumirResponsabilidadeLegada(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @Valid @RequestBody ReassumirResponsabilidadeRequest request) {
        fluxoPresenca.rejeitarReassuncaoLegada(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/responsabilidade-operacional/solicitacoes")
    public CompraResponse solicitarResponsabilidade(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        fluxoPresenca.solicitarResponsabilidade(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/responsabilidade-operacional/solicitacoes/{solicitacaoId}/cancelar")
    public CompraResponse cancelarSolicitacaoResponsabilidade(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID solicitacaoId) {
        fluxoPresenca.cancelarSolicitacaoResponsabilidade(usuario.getId(), familiaId, listaId, solicitacaoId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/solicitacoes-responsabilidade/{solicitacaoId}/{decisao:aprovar|rejeitar}")
    public CompraResponse decidirSolicitacaoResponsabilidade(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID solicitacaoId, @PathVariable String decisao) {
        fluxoPresenca.decidirResponsabilidade(usuario.getId(), familiaId, listaId, solicitacaoId, "aprovar".equals(decisao));
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/finalizar")
    public CompraResponse finalizar(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        finalizarCompra.executar(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/responsabilidade-operacional/transferir")
    public CompraResponse transferirResponsabilidade(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @Valid @RequestBody TransferirResponsabilidadeRequest request) {
        fluxoPresenca.transferirDiretamente(usuario.getId(), familiaId, listaId, request.participanteCompraId());
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/finalizar-administrativamente")
    public CompraResponse finalizarAdministrativamente(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        finalizarCompra.executarAdministrativamente(usuario.getId(), familiaId, listaId);
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @GetMapping
    public CompraResponse consultar(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        return CompraResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId), usuario.getId());
    }

    @PostMapping("/itens/{itemCompraId}/colocar-no-carrinho")
    public ItemCompraResponse colocarNoCarrinho(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID itemCompraId) {
        colocarItemNoCarrinho.executar(usuario.getId(), familiaId, listaId, itemCompraId);
        return itemResponse(familiaId, listaId, itemCompraId);
    }

    @PostMapping("/itens")
    public ResponseEntity<ItemCompraResponse> adicionarItem(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @Valid @RequestBody AdicionarItemDuranteCompraRequest request) {
        var item = adicionarItemDuranteCompra.executar(usuario.getId(), familiaId, listaId,
                new AdicionarItemDuranteCompraCommand(request.descricao(), request.quantidade(), request.unidadeMedida(),
                        request.marca(), request.observacoes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(itemResponse(familiaId, listaId, item.getId()));
    }

    @PostMapping("/itens/{itemCompraId}/solicitar-remocao")
    public ItemCompraResponse solicitarRemocao(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID itemCompraId) {
        solicitarRemocao.executar(usuario.getId(), familiaId, listaId, itemCompraId);
        return itemResponse(familiaId, listaId, itemCompraId);
    }

    @PostMapping("/itens/{itemCompraId}/aprovar-remocao")
    public ItemCompraResponse aprovarRemocao(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID itemCompraId) {
        aprovarRemocao.executar(usuario.getId(), familiaId, listaId, itemCompraId);
        return itemResponse(familiaId, listaId, itemCompraId);
    }

    @PostMapping("/itens/{itemCompraId}/rejeitar-remocao")
    public ItemCompraResponse rejeitarRemocao(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID itemCompraId) {
        rejeitarRemocao.executar(usuario.getId(), familiaId, listaId, itemCompraId);
        return itemResponse(familiaId, listaId, itemCompraId);
    }

    @PostMapping("/itens/{itemCompraId}/restaurar-no-carrinho")
    public ItemCompraResponse restaurarNoCarrinho(@PathVariable UUID familiaId, @PathVariable UUID listaId,
            @PathVariable UUID itemCompraId) {
        restaurarItemNoCarrinho.executar(usuario.getId(), familiaId, listaId, itemCompraId);
        return itemResponse(familiaId, listaId, itemCompraId);
    }

    private ItemCompraResponse itemResponse(UUID familiaId, UUID listaId, UUID itemCompraId) {
        var resultado = consultarCompra.consultar(usuario.getId(), familiaId, listaId);
        return resultado.itens().stream()
                .filter(item -> item.getId().equals(itemCompraId))
                .findFirst()
                .map(new ItemCompraResponseMapper(resultado, usuario.getId())::from)
                .orElseThrow(() -> new IllegalStateException("Item da compra nao encontrado apos operacao."));
    }
}
