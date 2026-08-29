package com.mercadeira.api.usuario.application;

import java.time.Clock;

import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CadastrarUsuario {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CadastrarUsuario(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha) {
        validarObrigatorio(nome, "nome");
        validarObrigatorio(email, "email");
        validarObrigatorio(senha, "senha");

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario = Usuario.criar(nome, email, passwordEncoder.encode(senha), clock.instant());
        return usuarioRepository.save(usuario);
    }

    private void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DadosUsuarioInvalidosException(campo);
        }
    }
}
