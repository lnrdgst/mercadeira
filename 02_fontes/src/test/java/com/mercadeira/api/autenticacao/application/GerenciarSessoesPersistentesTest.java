package com.mercadeira.api.autenticacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.mercadeira.api.autenticacao.domain.SessaoPersistente;
import com.mercadeira.api.autenticacao.repository.SessaoPersistenteRepository;
import com.mercadeira.api.autenticacao.security.EmissorTokenJwt;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GerenciarSessoesPersistentesTest {
    private final SessaoPersistenteRepository sessoes = mock(SessaoPersistenteRepository.class);
    private final EmissorTokenJwt emissor = mock(EmissorTokenJwt.class);
    private final Usuario usuario = mock(Usuario.class);
    private final Instant agora = Instant.parse("2026-10-02T12:00:00Z");
    private GerenciarSessoesPersistentes service;

    @BeforeEach
    void configurar() {
        SessionProperties properties = new SessionProperties();
        properties.setExpirationDays(180);
        when(emissor.emitirPara(any())).thenReturn(new TokenAutenticacao("access-token", agora.plus(Duration.ofHours(1))));
        service = new GerenciarSessoesPersistentes(sessoes, emissor, properties, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void criaSessaoComRefreshAleatorioEPersisteApenasHashPorCentoEOitentaDias() {
        SessaoAutenticada resultado = service.criar(usuario);

        ArgumentCaptor<SessaoPersistente> sessao = ArgumentCaptor.forClass(SessaoPersistente.class);
        verify(sessoes).save(sessao.capture());
        assertThat(resultado.accessToken().token()).isEqualTo("access-token");
        assertThat(resultado.refreshToken()).hasSizeGreaterThan(40);
        assertThat(sessao.getValue().getTokenHash()).isEqualTo(GerenciarSessoesPersistentes.hash(resultado.refreshToken()))
                .isNotEqualTo(resultado.refreshToken());
        assertThat(sessao.getValue().getExpiraEm()).isEqualTo(agora.plus(Duration.ofDays(180)));
        assertThat(sessao.getValue().getUltimoUsoEm()).isEqualTo(agora);
    }

    @Test
    void refreshValidoRotacionaTokenSemEstenderExpiracaoDaSessao() {
        SessaoPersistente sessao = SessaoPersistente.criar(usuario, GerenciarSessoesPersistentes.hash("token-atual"),
                agora.minus(Duration.ofDays(1)), agora.plus(Duration.ofDays(179)));
        when(sessoes.findByTokenHash(GerenciarSessoesPersistentes.hash("token-atual")))
                .thenReturn(Optional.of(sessao), Optional.empty());

        SessaoAutenticada resultado = service.renovar("token-atual");

        assertThat(resultado.accessToken().token()).isEqualTo("access-token");
        assertThat(resultado.refreshToken()).isNotEqualTo("token-atual");
        assertThat(sessao.getTokenHash()).isEqualTo(GerenciarSessoesPersistentes.hash(resultado.refreshToken()))
                .isNotEqualTo(GerenciarSessoesPersistentes.hash("token-atual"));
        assertThat(sessao.getExpiraEm()).isEqualTo(agora.plus(Duration.ofDays(179)));
        assertThat(sessao.getUltimoUsoEm()).isEqualTo(agora);

        assertThatThrownBy(() -> service.renovar("token-atual")).isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void rejeitaRefreshExpiradoRevogadoOuInexistente() {
        SessaoPersistente expirada = SessaoPersistente.criar(usuario, GerenciarSessoesPersistentes.hash("expirado"),
                agora.minus(Duration.ofDays(180)), agora);
        SessaoPersistente revogada = SessaoPersistente.criar(usuario, GerenciarSessoesPersistentes.hash("revogado"),
                agora.minus(Duration.ofDays(1)), agora.plus(Duration.ofDays(1)));
        revogada.revogar(agora.minusSeconds(1));
        when(sessoes.findByTokenHash(GerenciarSessoesPersistentes.hash("expirado"))).thenReturn(Optional.of(expirada));
        when(sessoes.findByTokenHash(GerenciarSessoesPersistentes.hash("revogado"))).thenReturn(Optional.of(revogada));
        when(sessoes.findByTokenHash(GerenciarSessoesPersistentes.hash("ausente"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("expirado")).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> service.renovar("revogado")).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> service.renovar("ausente")).isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void logoutRevogaSomenteASessaoDoRefreshInformado() {
        SessaoPersistente sessao = SessaoPersistente.criar(usuario, GerenciarSessoesPersistentes.hash("token"), agora,
                agora.plus(Duration.ofDays(180)));
        when(sessoes.findByTokenHash(GerenciarSessoesPersistentes.hash("token"))).thenReturn(Optional.of(sessao));

        service.revogar("token");

        assertThat(sessao.getRevogadoEm()).isEqualTo(agora);
    }

    @Test
    void revogaTodasAsSessoesAoAlterarCredencial() {
        UUID usuarioId = UUID.randomUUID();

        service.revogarTodasDoUsuario(usuarioId);

        verify(sessoes).revogarAtivasDoUsuario(eq(usuarioId), eq(agora));
    }
}
