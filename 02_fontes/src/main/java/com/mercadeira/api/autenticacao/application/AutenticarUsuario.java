package com.mercadeira.api.autenticacao.application;

import com.mercadeira.api.autenticacao.security.EmissorTokenJwt;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarUsuario {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmissorTokenJwt emissorTokenJwt;

    public AutenticarUsuario(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmissorTokenJwt emissorTokenJwt) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emissorTokenJwt = emissorTokenJwt;
    }

    @Transactional(readOnly = true)
    public TokenAutenticacao autenticar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);
        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        return emissorTokenJwt.emitirPara(usuario);
    }
}
