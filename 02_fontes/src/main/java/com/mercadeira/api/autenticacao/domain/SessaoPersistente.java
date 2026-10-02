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
@Table(name = "sessao_persistente")
public class SessaoPersistente {
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
    @Column(name = "ultimo_uso_em", nullable = false)
    private Instant ultimoUsoEm;
    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected SessaoPersistente() { }

    public static SessaoPersistente criar(Usuario usuario, String tokenHash, Instant agora, Instant expiraEm) {
        SessaoPersistente sessao = new SessaoPersistente();
        sessao.usuario = usuario;
        sessao.tokenHash = tokenHash;
        sessao.criadoEm = agora;
        sessao.expiraEm = expiraEm;
        sessao.ultimoUsoEm = agora;
        return sessao;
    }

    public boolean podeSerUsadaEm(Instant agora) {
        return revogadoEm == null && expiraEm.isAfter(agora);
    }

    public void rotacionar(String novoTokenHash, Instant agora) {
        this.tokenHash = novoTokenHash;
        this.ultimoUsoEm = agora;
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) revogadoEm = agora;
    }

    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getUltimoUsoEm() { return ultimoUsoEm; }
    public Instant getRevogadoEm() { return revogadoEm; }
}
