package com.mercadeira.api.compra.api;

import java.net.URI;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.application.ConsultarCompraDaLista;
import com.mercadeira.api.compra.application.IniciarCompra;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familias/{familiaId}/listas/{listaId}/compra")
public class CompraController {

    private final UsuarioAutenticado usuario;
    private final IniciarCompra iniciarCompra;
    private final ConsultarCompraDaLista consultarCompra;

    public CompraController(UsuarioAutenticado usuario, IniciarCompra iniciarCompra,
            ConsultarCompraDaLista consultarCompra) {
        this.usuario = usuario;
        this.iniciarCompra = iniciarCompra;
        this.consultarCompra = consultarCompra;
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
}
