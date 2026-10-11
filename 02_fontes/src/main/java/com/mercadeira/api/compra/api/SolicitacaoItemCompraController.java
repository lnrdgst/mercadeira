package com.mercadeira.api.compra.api;
import java.net.URI; import java.util.UUID; import com.mercadeira.api.autenticacao.security.UsuarioAutenticado; import com.mercadeira.api.compra.application.*; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/compras/{compraId}/solicitacoes-itens") public class SolicitacaoItemCompraController {
 private final UsuarioAutenticado usuario; private final SolicitarItemCompraRemoto service;
 public SolicitacaoItemCompraController(UsuarioAutenticado u,SolicitarItemCompraRemoto s){usuario=u;service=s;}
 @PostMapping public ResponseEntity<SolicitacaoItemCompraResponse> criar(@PathVariable UUID compraId,@Valid @RequestBody SolicitacaoItemCompraRequest r){var s=service.solicitar(usuario.getId(),compraId,new SolicitarItemCompraRemotoCommand(r.descricao(),r.quantidade(),r.unidadeMedida()));return ResponseEntity.created(URI.create("/api/compras/"+compraId+"/solicitacoes-itens/"+s.getId())).body(SolicitacaoItemCompraResponse.from(s));}
}
