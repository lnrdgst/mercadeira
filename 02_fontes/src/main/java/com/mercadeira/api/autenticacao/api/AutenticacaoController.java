package com.mercadeira.api.autenticacao.api;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.autenticacao.application.AutenticarComGoogle;
import com.mercadeira.api.autenticacao.application.GerenciarSessoesPersistentes;
import com.mercadeira.api.autenticacao.application.SessaoAutenticada;
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
    private final AutenticarComGoogle autenticarComGoogle;
    private final RecuperarSenha recuperarSenha;
    private final GerenciarSessoesPersistentes sessoes;

    public AutenticacaoController(AutenticarUsuario autenticarUsuario, AutenticarComGoogle autenticarComGoogle, RecuperarSenha recuperarSenha,
            GerenciarSessoesPersistentes sessoes) {
        this.autenticarUsuario = autenticarUsuario;
        this.autenticarComGoogle = autenticarComGoogle;
        this.recuperarSenha = recuperarSenha;
        this.sessoes = sessoes;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SessaoAutenticada sessao = autenticarUsuario.autenticarComSessao(request.email(), request.senha());
        return ResponseEntity.ok(LoginResponse.from(sessao));
    }

    @PostMapping("/google")
    public ResponseEntity<GoogleLoginResponse> loginGoogle(@Valid @RequestBody GoogleCredentialRequest request) {
        return ResponseEntity.ok(GoogleLoginResponse.from(autenticarComGoogle.autenticar(request.credential())));
    }

    @PostMapping("/google/vincular")
    public ResponseEntity<GoogleLoginResponse> vincularGoogle(@Valid @RequestBody VincularGoogleRequest request) {
        return ResponseEntity.ok(GoogleLoginResponse.from(autenticarComGoogle.vincular(request.credential(), request.senhaAtual())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(LoginResponse.from(sessoes.renovar(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        sessoes.revogar(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
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
