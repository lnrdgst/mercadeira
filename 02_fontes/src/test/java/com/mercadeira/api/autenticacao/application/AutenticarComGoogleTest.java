package com.mercadeira.api.autenticacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.google.GoogleCredentialValidator;
import com.mercadeira.api.autenticacao.google.GoogleIdentity;
import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.domain.UsuarioIdentidade;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AutenticarComGoogleTest {
    private final GoogleCredentialValidator validator = mock(GoogleCredentialValidator.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuarioIdentidadeRepository identidades = mock(UsuarioIdentidadeRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final GerenciarSessoesPersistentes sessoes = mock(GerenciarSessoesPersistentes.class);
    private AutenticarComGoogle service;

    @BeforeEach void setup() {
        service = new AutenticarComGoogle(validator, usuarios, identidades, encoder, sessoes,
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test void primeiroAcessoCriaUsuarioGoogleSemSenhaNemIdentidadeLocal() {
        GoogleIdentity google = new GoogleIdentity("sub-123", "ana@example.test", "Ana Google");
        Usuario usuario = mock(Usuario.class);
        when(validator.validar("credential")).thenReturn(google);
        when(usuarios.existsByEmail("ana@example.test")).thenReturn(false);
        when(usuarios.save(any())).thenReturn(usuario);
        when(sessoes.criar(usuario)).thenReturn(sessao());

        ResultadoAutenticacaoGoogle resultado = service.autenticar("credential");

        assertThat(resultado.vinculoNecessario()).isFalse();
        verify(usuarios).save(org.mockito.ArgumentMatchers.argThat(criado -> criado.getSenhaHash() == null));
        verify(identidades).save(any(UsuarioIdentidade.class));
        verify(sessoes).criar(usuario);
    }

    @Test void mesmoSubjectReutilizaUsuarioMesmoComEmailGoogleAlterado() {
        GoogleIdentity google = new GoogleIdentity("sub-123", "novo@example.test", "Ana");
        Usuario usuario = mock(Usuario.class);
        UsuarioIdentidade identidade = mock(UsuarioIdentidade.class);
        when(validator.validar("credential")).thenReturn(google);
        when(identidades.findByProvedorAndProvedorSubject(ProvedorIdentidadeUsuario.GOOGLE, "sub-123"))
                .thenReturn(Optional.of(identidade));
        when(identidade.getUsuario()).thenReturn(usuario);
        when(sessoes.criar(usuario)).thenReturn(sessao());

        ResultadoAutenticacaoGoogle resultado = service.autenticar("credential");

        assertThat(resultado.vinculoNecessario()).isFalse();
        verify(identidade).atualizarEmailProvedor(org.mockito.ArgumentMatchers.eq("novo@example.test"), any());
        verify(usuarios, org.mockito.Mockito.never()).save(any());
    }

    @Test void emailDeContaLocalExistenteExigeVinculoSemCriarUsuario() {
        when(validator.validar("credential")).thenReturn(new GoogleIdentity("sub-123", "ana@example.test", "Ana"));
        when(usuarios.existsByEmail("ana@example.test")).thenReturn(true);

        assertThat(service.autenticar("credential").vinculoNecessario()).isTrue();
        verify(usuarios, org.mockito.Mockito.never()).save(any());
        verify(identidades, org.mockito.Mockito.never()).save(any());
    }

    @Test void vinculaComSenhaLocalValidaMantendoUsuario() {
        UUID id = UUID.randomUUID();
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id); when(usuario.getSenhaHash()).thenReturn("hash");
        when(validator.validar("credential")).thenReturn(new GoogleIdentity("sub-123", "ana@example.test", "Ana"));
        when(usuarios.findByEmail("ana@example.test")).thenReturn(Optional.of(usuario));
        when(identidades.existsByUsuarioIdAndProvedor(id, ProvedorIdentidadeUsuario.LOCAL)).thenReturn(true);
        when(encoder.matches("senha", "hash")).thenReturn(true);
        when(identidades.findByProvedorAndProvedorSubject(ProvedorIdentidadeUsuario.GOOGLE, "sub-123")).thenReturn(Optional.empty());
        when(identidades.existsByUsuarioIdAndProvedor(id, ProvedorIdentidadeUsuario.GOOGLE)).thenReturn(false);
        when(sessoes.criar(usuario)).thenReturn(sessao());

        service.vincular("credential", "senha");

        verify(identidades).save(any(UsuarioIdentidade.class));
        verify(sessoes).criar(usuario);
    }

    @Test void senhaIncorretaNaoVincula() {
        Usuario usuario = mock(Usuario.class); UUID id = UUID.randomUUID();
        when(usuario.getId()).thenReturn(id); when(usuario.getSenhaHash()).thenReturn("hash");
        when(validator.validar("credential")).thenReturn(new GoogleIdentity("sub-123", "ana@example.test", "Ana"));
        when(usuarios.findByEmail("ana@example.test")).thenReturn(Optional.of(usuario));
        when(identidades.existsByUsuarioIdAndProvedor(id, ProvedorIdentidadeUsuario.LOCAL)).thenReturn(true);
        when(encoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.vincular("credential", "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        verify(identidades, org.mockito.Mockito.never()).save(any());
    }

    private static SessaoAutenticada sessao() {
        return new SessaoAutenticada(new TokenAutenticacao("access", Instant.now().plusSeconds(60)), "refresh");
    }
}
