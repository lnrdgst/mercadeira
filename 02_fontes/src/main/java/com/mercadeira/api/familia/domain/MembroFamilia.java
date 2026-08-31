package com.mercadeira.api.familia.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "membro_familia")
public class MembroFamilia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 30)
    private PapelMembroFamilia papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMembroFamilia status;

    @Column(name = "apelido", length = 120)
    private String apelido;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected MembroFamilia() {
    }

    public static MembroFamilia criarAdministrador(Familia familia, Usuario usuario, Instant agora) {
        return criar(familia, usuario, PapelMembroFamilia.ADMINISTRADOR, agora);
    }

    public static MembroFamilia criarMembro(Familia familia, Usuario usuario, Instant agora) {
        return criar(familia, usuario, PapelMembroFamilia.MEMBRO, agora);
    }

    private static MembroFamilia criar(Familia familia, Usuario usuario, PapelMembroFamilia papel, Instant agora) {
        MembroFamilia membro = new MembroFamilia();
        membro.familia = familia;
        membro.usuario = usuario;
        membro.papel = papel;
        membro.status = StatusMembroFamilia.ATIVO;
        membro.criadoEm = agora;
        membro.atualizadoEm = agora;
        return membro;
    }

    public UUID getId() {
        return id;
    }

    public Familia getFamilia() {
        return familia;
    }

    public PapelMembroFamilia getPapel() {
        return papel;
    }

    public StatusMembroFamilia getStatus() {
        return status;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
