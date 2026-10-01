package com.mercadeira.api.autenticacao.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.mercadeira.api.autenticacao.domain.TokenRedefinicaoSenha;
import com.mercadeira.api.autenticacao.email.EmailService;
import com.mercadeira.api.autenticacao.repository.TokenRedefinicaoSenhaRepository;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecuperarSenha {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final TokenRedefinicaoSenhaRepository tokens;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final Clock clock;

    public RecuperarSenha(UsuarioRepository usuarios, TokenRedefinicaoSenhaRepository tokens, EmailService emailService,
            PasswordEncoder passwordEncoder, PasswordResetProperties properties, Clock clock) {
        this.usuarios = usuarios; this.tokens = tokens; this.emailService = emailService;
        this.passwordEncoder = passwordEncoder; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public void solicitar(String email) {
        String emailNormalizado = CadastrarUsuario.normalizarEmail(email);
        Usuario usuario = usuarios.findByEmail(emailNormalizado).orElse(null);
        if (usuario == null) return;
        Instant agora = clock.instant();
        if (tokens.findTopByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(usuario.getId())
                .filter(token -> token.getCriadoEm().plusSeconds(properties.getCooldownSeconds()).isAfter(agora)).isPresent()) return;
        tokens.invalidarAtivosDoUsuario(usuario.getId(), agora);
        String tokenPuro = gerarToken();
        tokens.save(TokenRedefinicaoSenha.criar(usuario, hash(tokenPuro), agora,
                agora.plus(Duration.ofMinutes(properties.getExpirationMinutes()))));
        emailService.enviarRedefinicaoSenha(usuario.getEmail(), link(tokenPuro),
                Duration.ofMinutes(properties.getExpirationMinutes()));
    }

    @Transactional
    public void redefinir(String tokenPuro, String novaSenha) {
        CadastrarUsuario.validarObrigatorio(tokenPuro, "token");
        CadastrarUsuario.validarObrigatorio(novaSenha, "novaSenha");
        Instant agora = clock.instant();
        TokenRedefinicaoSenha token = tokens.findByTokenHash(hash(tokenPuro))
                .orElseThrow(TokenRedefinicaoInvalidoException::new);
        if (!token.podeSerUsadoEm(agora)) throw new TokenRedefinicaoInvalidoException();
        Usuario usuario = token.getUsuario();
        usuario.alterarSenha(passwordEncoder.encode(novaSenha), agora);
        token.marcarComoUsado(agora);
        tokens.invalidarAtivosDoUsuario(usuario.getId(), agora);
    }

    static String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 indisponivel", exception); }
    }
    private static String gerarToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String link(String token) { return properties.getFrontendUrl().replaceAll("/+$", "") + "/redefinir-senha?token=" + token; }
}
