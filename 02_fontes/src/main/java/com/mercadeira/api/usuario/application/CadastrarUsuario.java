package com.mercadeira.api.usuario.application;

import java.time.Clock;

import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.domain.UsuarioIdentidade;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CadastrarUsuario {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioIdentidadeRepository identidadeRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CadastrarUsuario(UsuarioRepository usuarioRepository, UsuarioIdentidadeRepository identidadeRepository,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.identidadeRepository = identidadeRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha) {
        nome = normalizarNome(nome);
        email = normalizarEmail(email);
        validarObrigatorio(senha, "senha");

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        var agora = clock.instant();
        Usuario usuario = usuarioRepository.save(Usuario.criar(nome, email, passwordEncoder.encode(senha), agora));
        identidadeRepository.save(UsuarioIdentidade.local(usuario, agora));
        return usuario;
    }

    public static String normalizarNome(String valor) {
        validarObrigatorio(valor, "nome");
        return valor.trim();
    }

    public static String normalizarEmail(String valor) {
        validarObrigatorio(valor, "email");
        return valor.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DadosUsuarioInvalidosException(campo);
        }
    }
}
