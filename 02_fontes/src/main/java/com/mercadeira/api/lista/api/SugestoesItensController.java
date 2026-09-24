package com.mercadeira.api.lista.api;

import java.util.List;
import java.util.UUID;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.lista.application.SugerirItensFamilia;
import com.mercadeira.api.lista.repository.SugestoesItensRepository.Sugestao;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/familias/{familiaId}/itens/sugestoes")
public class SugestoesItensController {
    private final UsuarioAutenticado usuario;
    private final SugerirItensFamilia sugestoes;
    public SugestoesItensController(UsuarioAutenticado usuario, SugerirItensFamilia sugestoes) {
        this.usuario = usuario; this.sugestoes = sugestoes;
    }
    @GetMapping
    public List<Sugestao> buscar(@PathVariable UUID familiaId, @RequestParam UUID listaId,
            @RequestParam CategoriaCompra categoria, @RequestParam(defaultValue = "") String termo) {
        return sugestoes.buscar(usuario.getId(), familiaId, listaId, categoria, termo);
    }
}
