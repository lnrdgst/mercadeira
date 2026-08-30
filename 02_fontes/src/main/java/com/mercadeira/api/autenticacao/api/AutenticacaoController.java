package com.mercadeira.api.autenticacao.api;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.autenticacao.application.TokenAutenticacao;
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

    public AutenticacaoController(AutenticarUsuario autenticarUsuario) {
        this.autenticarUsuario = autenticarUsuario;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenAutenticacao token = autenticarUsuario.autenticar(request.email(), request.senha());
        return ResponseEntity.ok(LoginResponse.from(token));
    }
}
