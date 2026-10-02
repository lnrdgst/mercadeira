package com.mercadeira.api.usuario.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.application.GerenciarSessoesPersistentes;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AtualizarMinhaContaTest {
    @Test
    void alterarSenhaRevogaTodasAsSessoesPersistentesDoUsuario() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        GerenciarSessoesPersistentes sessoes = mock(GerenciarSessoesPersistentes.class);
        AtualizarMinhaConta service = new AtualizarMinhaConta(usuarios, encoder, sessoes,
                Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC));
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = mock(Usuario.class);
        when(usuarios.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuario.getSenhaHash()).thenReturn("hash-atual");
        when(encoder.matches("senha-atual", "hash-atual")).thenReturn(true);
        when(encoder.encode("senha-nova")).thenReturn("novo-hash");

        service.senha(usuarioId, "senha-atual", "senha-nova");

        verify(usuario).alterarSenha(org.mockito.ArgumentMatchers.eq("novo-hash"), any());
        verify(sessoes).revogarTodasDoUsuario(usuarioId);
    }
}
