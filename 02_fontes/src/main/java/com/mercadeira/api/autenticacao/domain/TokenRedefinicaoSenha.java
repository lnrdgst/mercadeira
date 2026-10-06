package com.mercadeira.api.autenticacao.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "token_redefinicao_senha")
public class TokenRedefinicaoSenha {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;
    @Column(name = "usado_em")
    private Instant usadoEm;

    protected TokenRedefinicaoSenha() { }
    public static TokenRedefinicaoSenha criar(Usuario usuario, String tokenHash, Instant criadoEm, Instant expiraEm) {
        TokenRedefinicaoSenha token = new TokenRedefinicaoSenha();
        token.usuario = usuario; token.tokenHash = tokenHash; token.criadoEm = criadoEm; token.expiraEm = expiraEm;
        return token;
    }
    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean podeSerUsadoEm(Instant agora) { return usadoEm == null && expiraEm.isAfter(agora); }
    public void marcarComoUsado(Instant agora) { if (usadoEm == null) usadoEm = agora; }
}
