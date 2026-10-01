package com.mercadeira.api.autenticacao.api;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.autenticacao.application.TokenAutenticacao;
import com.mercadeira.api.autenticacao.application.RecuperarSenha;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/autenticacao")
public class AutenticacaoController {

    private final AutenticarUsuario autenticarUsuario;
    private final RecuperarSenha recuperarSenha;

    public AutenticacaoController(AutenticarUsuario autenticarUsuario, RecuperarSenha recuperarSenha) {
        this.autenticarUsuario = autenticarUsuario;
        this.recuperarSenha = recuperarSenha;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenAutenticacao token = autenticarUsuario.autenticar(request.email(), request.senha());
        return ResponseEntity.ok(LoginResponse.from(token));
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<MensagemGenericaResponse> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request) {
        recuperarSenha.solicitar(request.email());
        return ResponseEntity.ok(new MensagemGenericaResponse());
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        recuperarSenha.redefinir(request.token(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
