package com.mercadeira.api.usuario.api;

import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final CadastrarUsuario cadastrarUsuario;
    private final UsuarioAutenticado usuarioAutenticado;
    private final UsuarioRepository usuarioRepository;

    public UsuarioController(CadastrarUsuario cadastrarUsuario, UsuarioAutenticado usuarioAutenticado, UsuarioRepository usuarioRepository) {
        this.cadastrarUsuario = cadastrarUsuario;
        this.usuarioAutenticado = usuarioAutenticado;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastrarUsuarioRequest request) {
        Usuario usuario = cadastrarUsuario.cadastrar(request.nome(), request.email(), request.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(usuario));
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return UsuarioResponse.from(usuarioRepository.findById(usuarioAutenticado.getId()).orElseThrow());
    }
}
