package com.mercadeira.api.usuario.api;

import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.application.AtualizarMinhaConta;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final CadastrarUsuario cadastrarUsuario;
    private final UsuarioAutenticado usuarioAutenticado;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioIdentidadeRepository identidadeRepository;
    private final AtualizarMinhaConta atualizarMinhaConta;

    public UsuarioController(CadastrarUsuario cadastrarUsuario, UsuarioAutenticado usuarioAutenticado, UsuarioRepository usuarioRepository, UsuarioIdentidadeRepository identidadeRepository, AtualizarMinhaConta atualizarMinhaConta) {
        this.cadastrarUsuario = cadastrarUsuario;
        this.usuarioAutenticado = usuarioAutenticado;
        this.usuarioRepository = usuarioRepository;
        this.identidadeRepository = identidadeRepository;
        this.atualizarMinhaConta = atualizarMinhaConta;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastrarUsuarioRequest request) {
        Usuario usuario = cadastrarUsuario.cadastrar(request.nome(), request.email(), request.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(usuario));
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return response(usuarioRepository.findById(usuarioAutenticado.getId()).orElseThrow());
    }

    @PatchMapping("/me")
    public UsuarioResponse atualizarMe(@Valid @RequestBody AtualizarMinhaContaRequest request) {
        return response(atualizarMinhaConta.dados(usuarioAutenticado.getId(), request.nome(), request.email(), request.senhaAtual()));
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> alterarSenha(@Valid @RequestBody AlterarSenhaRequest request) {
        atualizarMinhaConta.senha(usuarioAutenticado.getId(), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    private UsuarioResponse response(Usuario usuario) {
        return UsuarioResponse.from(usuario, identidadeRepository.findByUsuarioId(usuario.getId()).stream()
                .map(identidade -> identidade.getProvedor()).toList());
    }
}
