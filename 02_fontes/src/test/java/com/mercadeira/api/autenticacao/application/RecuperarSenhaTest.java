package com.mercadeira.api.autenticacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.domain.TokenRedefinicaoSenha;
import com.mercadeira.api.autenticacao.email.EmailService;
import com.mercadeira.api.autenticacao.repository.TokenRedefinicaoSenhaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class RecuperarSenhaTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuarioIdentidadeRepository identidades = mock(UsuarioIdentidadeRepository.class);
    private final TokenRedefinicaoSenhaRepository tokens = mock(TokenRedefinicaoSenhaRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final GerenciarSessoesPersistentes sessoes = mock(GerenciarSessoesPersistentes.class);
    private final Instant agora = Instant.parse("2026-10-01T12:00:00Z");
    private RecuperarSenha service;

    @BeforeEach void configurar() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setFrontendUrl("https://app.example.test/"); properties.setExpirationMinutes(30); properties.setCooldownSeconds(60);
        service = new RecuperarSenha(usuarios, identidades, tokens, email, encoder, properties, sessoes, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test void solicitaNormalizaEmailPersisteSomenteHashEEnviaLink() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(UUID.randomUUID()); when(usuario.getEmail()).thenReturn("ana@example.test");
        when(usuarios.findByEmail("ana@example.test")).thenReturn(Optional.of(usuario));
        when(identidades.existsByUsuarioIdAndProvedor(usuario.getId(), ProvedorIdentidadeUsuario.LOCAL)).thenReturn(true);
        when(tokens.findTopByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(usuario.getId())).thenReturn(Optional.empty());
        service.solicitar("  ANA@EXAMPLE.TEST ");
        ArgumentCaptor<TokenRedefinicaoSenha> token = ArgumentCaptor.forClass(TokenRedefinicaoSenha.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(token.capture()); verify(email).enviarRedefinicaoSenha(eq("ana@example.test"), link.capture(), any());
        String tokenPuro = URI.create(link.getValue()).getQuery().substring("token=".length());
        assertThat(token.getValue().getTokenHash()).isEqualTo(RecuperarSenha.hash(tokenPuro)).isNotEqualTo(tokenPuro);
        assertThat(token.getValue().getCriadoEm()).isEqualTo(agora);
        verify(tokens).invalidarAtivosDoUsuario(usuario.getId(), agora);
    }

    @Test void contaSomenteGoogleNaoRecebeTokenDeRedefinicao() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(UUID.randomUUID());
        when(usuarios.findByEmail("ana@example.test")).thenReturn(Optional.of(usuario));
        when(identidades.existsByUsuarioIdAndProvedor(usuario.getId(), ProvedorIdentidadeUsuario.LOCAL)).thenReturn(false);

        service.solicitar("ana@example.test");

        verify(tokens, org.mockito.Mockito.never()).save(any());
        verify(email, org.mockito.Mockito.never()).enviarRedefinicaoSenha(any(), any(), any());
    }

    @Test void emailInexistenteNaoRevelaExistenciaNemEmiteToken() {
        when(usuarios.findByEmail("ausente@example.test")).thenReturn(Optional.empty());
        service.solicitar("ausente@example.test");
        verify(usuarios).findByEmail("ausente@example.test");
        verify(tokens, org.mockito.Mockito.never()).save(any());
        verify(email, org.mockito.Mockito.never()).enviarRedefinicaoSenha(any(), any(), any());
    }

    @Test void tokenExpiradoNaoAlteraSenha() {
        TokenRedefinicaoSenha token = mock(TokenRedefinicaoSenha.class);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(token.podeSerUsadoEm(agora)).thenReturn(false);
        assertThatThrownBy(() -> service.redefinir("token", "nova-senha")).isInstanceOf(TokenRedefinicaoInvalidoException.class);
        verify(encoder, org.mockito.Mockito.never()).encode(any());
    }

    @Test void redefinicaoDeSenhaRevogaTodasAsSessoesPersistentes() {
        TokenRedefinicaoSenha token = mock(TokenRedefinicaoSenha.class);
        Usuario usuario = mock(Usuario.class);
        UUID usuarioId = UUID.randomUUID();
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(token.podeSerUsadoEm(agora)).thenReturn(true);
        when(token.getUsuario()).thenReturn(usuario);
        when(usuario.getId()).thenReturn(usuarioId);

        service.redefinir("token", "nova-senha");

        verify(sessoes).revogarTodasDoUsuario(usuarioId);
    }
}
