package com.mercadeira.api.compra.api;

import java.net.URI;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.application.ConsultarCompraDaLista;
import com.mercadeira.api.compra.application.AdicionarItemDuranteCompra;
import com.mercadeira.api.compra.application.AdicionarItemDuranteCompraCommand;
import com.mercadeira.api.compra.application.ColocarItemNoCarrinho;
import com.mercadeira.api.compra.application.IniciarCompra;
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

    private final UsuarioAutenticado usuario;
    private final IniciarCompra iniciarCompra;
    private final ConsultarCompraDaLista consultarCompra;
    private final ColocarItemNoCarrinho colocarItemNoCarrinho;
    private final AdicionarItemDuranteCompra adicionarItemDuranteCompra;

    public CompraController(UsuarioAutenticado usuario, IniciarCompra iniciarCompra,
            ConsultarCompraDaLista consultarCompra, ColocarItemNoCarrinho colocarItemNoCarrinho,
            AdicionarItemDuranteCompra adicionarItemDuranteCompra) {
        this.usuario = usuario;
        this.iniciarCompra = iniciarCompra;
        this.consultarCompra = consultarCompra;
        this.colocarItemNoCarrinho = colocarItemNoCarrinho;
        this.adicionarItemDuranteCompra = adicionarItemDuranteCompra;
    }

    @PostMapping
    public ResponseEntity<CompraAtivaResponse> iniciar(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        var resultado = iniciarCompra.iniciar(usuario.getId(), familiaId, listaId);
        var response = CompraAtivaResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId));
        if (!resultado.criada()) {
            return ResponseEntity.ok(response);
        }
        URI location = URI.create("/api/familias/" + familiaId + "/listas/" + listaId + "/compra");
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public CompraAtivaResponse consultar(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        return CompraAtivaResponse.from(consultarCompra.consultar(usuario.getId(), familiaId, listaId));
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

    private ItemCompraResponse itemResponse(UUID familiaId, UUID listaId, UUID itemCompraId) {
        var resultado = consultarCompra.consultar(usuario.getId(), familiaId, listaId);
        return resultado.itens().stream()
                .filter(item -> item.getId().equals(itemCompraId))
                .findFirst()
                .map(item -> ItemCompraResponse.from(item, resultado.participantes()))
                .orElseThrow(() -> new IllegalStateException("Item da compra nao encontrado apos operacao."));
    }
}
