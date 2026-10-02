package com.mercadeira.api.autenticacao.application;

import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarUsuario {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final GerenciarSessoesPersistentes sessoes;

    public AutenticarUsuario(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            GerenciarSessoesPersistentes sessoes) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessoes = sessoes;
    }

    @Transactional
    public TokenAutenticacao autenticar(String email, String senha) {
        return autenticarComSessao(email, senha).accessToken();
    }

    @Transactional
    public SessaoAutenticada autenticarComSessao(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);
        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        return sessoes.criar(usuario);
    }
}
