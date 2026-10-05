package com.mercadeira.api.autenticacao.application;

import java.time.Clock;

import com.mercadeira.api.autenticacao.google.GoogleCredentialValidator;
import com.mercadeira.api.autenticacao.google.GoogleIdentity;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.domain.UsuarioIdentidade;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarComGoogle {
    private final GoogleCredentialValidator validator;
    private final UsuarioRepository usuarios;
    private final UsuarioIdentidadeRepository identidades;
    private final PasswordEncoder passwordEncoder;
    private final GerenciarSessoesPersistentes sessoes;
    private final Clock clock;

    public AutenticarComGoogle(GoogleCredentialValidator validator, UsuarioRepository usuarios,
            UsuarioIdentidadeRepository identidades, PasswordEncoder passwordEncoder,
            GerenciarSessoesPersistentes sessoes, Clock clock) {
        this.validator = validator; this.usuarios = usuarios; this.identidades = identidades;
        this.passwordEncoder = passwordEncoder; this.sessoes = sessoes; this.clock = clock;
    }

    @Transactional
    public ResultadoAutenticacaoGoogle autenticar(String credential) {
        GoogleIdentity google = validator.validar(credential);
        var existente = identidades.findByProvedorAndProvedorSubject(ProvedorIdentidadeUsuario.GOOGLE, google.subject());
        if (existente.isPresent()) {
            existente.get().atualizarEmailProvedor(normalizarEmail(google.email()), clock.instant());
            return ResultadoAutenticacaoGoogle.autenticado(sessoes.criar(existente.get().getUsuario()));
        }
        String email = normalizarEmail(google.email());
        if (usuarios.existsByEmail(email)) return ResultadoAutenticacaoGoogle.requerVinculo();
        var agora = clock.instant();
        Usuario usuario = usuarios.save(Usuario.criarSemSenha(nomeInicial(google, email), email, agora));
        identidades.save(UsuarioIdentidade.google(usuario, google.subject(), email, agora));
        return ResultadoAutenticacaoGoogle.autenticado(sessoes.criar(usuario));
    }

    @Transactional
    public SessaoAutenticada vincular(String credential, String senhaAtual) {
        GoogleIdentity google = validator.validar(credential);
        String email = normalizarEmail(google.email());
        Usuario usuario = usuarios.findByEmail(email).orElseThrow(CredenciaisInvalidasException::new);
        if (!identidades.existsByUsuarioIdAndProvedor(usuario.getId(), ProvedorIdentidadeUsuario.LOCAL)
                || usuario.getSenhaHash() == null || !passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        var porSubject = identidades.findByProvedorAndProvedorSubject(ProvedorIdentidadeUsuario.GOOGLE, google.subject());
        if (porSubject.isPresent()) {
            if (!porSubject.get().getUsuario().getId().equals(usuario.getId())) throw new CredenciaisInvalidasException();
            return sessoes.criar(usuario);
        }
        if (identidades.existsByUsuarioIdAndProvedor(usuario.getId(), ProvedorIdentidadeUsuario.GOOGLE)) {
            throw new CredenciaisInvalidasException();
        }
        identidades.save(UsuarioIdentidade.google(usuario, google.subject(), email, clock.instant()));
        return sessoes.criar(usuario);
    }

    private static String normalizarEmail(String email) { return CadastrarUsuario.normalizarEmail(email); }
    private static String nomeInicial(GoogleIdentity google, String email) {
        return google.nome() == null || google.nome().isBlank() ? email.substring(0, email.indexOf('@')) : CadastrarUsuario.normalizarNome(google.nome());
    }
}
