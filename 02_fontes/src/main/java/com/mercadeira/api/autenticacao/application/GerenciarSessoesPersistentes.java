package com.mercadeira.api.autenticacao.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.autenticacao.domain.SessaoPersistente;
import com.mercadeira.api.autenticacao.repository.SessaoPersistenteRepository;
import com.mercadeira.api.autenticacao.security.EmissorTokenJwt;
import com.mercadeira.api.usuario.domain.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GerenciarSessoesPersistentes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessaoPersistenteRepository sessoes;
    private final EmissorTokenJwt emissorTokenJwt;
    private final SessionProperties properties;
    private final Clock clock;

    public GerenciarSessoesPersistentes(SessaoPersistenteRepository sessoes, EmissorTokenJwt emissorTokenJwt,
            SessionProperties properties, Clock clock) {
        this.sessoes = sessoes;
        this.emissorTokenJwt = emissorTokenJwt;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SessaoAutenticada criar(Usuario usuario) {
        Instant agora = clock.instant();
        String refreshToken = gerarToken();
        sessoes.save(SessaoPersistente.criar(usuario, hash(refreshToken), agora,
                agora.plus(Duration.ofDays(validarDuracao()))));
        return new SessaoAutenticada(emissorTokenJwt.emitirPara(usuario), refreshToken);
    }

    @Transactional
    public SessaoAutenticada renovar(String refreshToken) {
        Instant agora = clock.instant();
        SessaoPersistente sessao = sessoes.findByTokenHash(hashObrigatorio(refreshToken))
                .orElseThrow(SessaoInvalidaException::new);
        if (!sessao.podeSerUsadaEm(agora)) throw new SessaoInvalidaException();
        String novoRefreshToken = gerarToken();
        sessao.rotacionar(hash(novoRefreshToken), agora);
        return new SessaoAutenticada(emissorTokenJwt.emitirPara(sessao.getUsuario()), novoRefreshToken);
    }

    @Transactional
    public void revogar(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) return;
        sessoes.findByTokenHash(hash(refreshToken)).ifPresent(sessao -> sessao.revogar(clock.instant()));
    }

    @Transactional
    public void revogarTodasDoUsuario(UUID usuarioId) {
        sessoes.revogarAtivasDoUsuario(usuarioId, clock.instant());
    }

    private int validarDuracao() {
        if (properties.getExpirationDays() <= 0) throw new IllegalStateException("SESSION_EXPIRATION_DAYS deve ser maior que zero.");
        return properties.getExpirationDays();
    }

    static String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private static String hashObrigatorio(String token) {
        if (!StringUtils.hasText(token)) throw new SessaoInvalidaException();
        return hash(token);
    }

    private static String gerarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
