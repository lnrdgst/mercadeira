package com.mercadeira.api.usuario.application;

import java.time.Clock;
import java.util.UUID;
import com.mercadeira.api.autenticacao.application.GerenciarSessoesPersistentes;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.domain.ProvedorIdentidadeUsuario;
import com.mercadeira.api.usuario.repository.UsuarioIdentidadeRepository;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtualizarMinhaConta {
    private final UsuarioRepository usuarios; private final UsuarioIdentidadeRepository identidades; private final PasswordEncoder passwordEncoder; private final GerenciarSessoesPersistentes sessoes; private final Clock clock;
    public AtualizarMinhaConta(UsuarioRepository usuarios, UsuarioIdentidadeRepository identidades, PasswordEncoder passwordEncoder, GerenciarSessoesPersistentes sessoes, Clock clock) { this.usuarios = usuarios; this.identidades = identidades; this.passwordEncoder = passwordEncoder; this.sessoes = sessoes; this.clock = clock; }
    @Transactional
    public Usuario dados(UUID usuarioId, String nome, String email, String senhaAtual) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        String nomeNormalizado = CadastrarUsuario.normalizarNome(nome);
        String emailNormalizado = CadastrarUsuario.normalizarEmail(email);
        boolean emailMudou = !usuario.getEmail().equalsIgnoreCase(emailNormalizado);
        boolean temLocal = identidades.existsByUsuarioIdAndProvedor(usuarioId, ProvedorIdentidadeUsuario.LOCAL);
        if (emailMudou && !temLocal) throw new FormaAcessoNaoDisponivelException();
        if (emailMudou && (usuario.getSenhaHash() == null || !passwordEncoder.matches(senhaAtual == null ? "" : senhaAtual, usuario.getSenhaHash()))) throw new SenhaAtualIncorretaException();
        if (emailMudou && usuarios.existsByEmailIgnoreCaseAndIdNot(emailNormalizado, usuarioId)) throw new EmailJaCadastradoException();
        usuario.alterarDadosPessoais(nomeNormalizado, emailNormalizado, clock.instant());
        return usuario;
    }
    @Transactional
    public void senha(UUID usuarioId, String senhaAtual, String novaSenha) {
        CadastrarUsuario.validarObrigatorio(senhaAtual, "senhaAtual"); CadastrarUsuario.validarObrigatorio(novaSenha, "novaSenha");
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        if (!identidades.existsByUsuarioIdAndProvedor(usuarioId, ProvedorIdentidadeUsuario.LOCAL) || usuario.getSenhaHash() == null) throw new FormaAcessoNaoDisponivelException();
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) throw new SenhaAtualIncorretaException();
        usuario.alterarSenha(passwordEncoder.encode(novaSenha), clock.instant());
        sessoes.revogarTodasDoUsuario(usuarioId);
    }
}
