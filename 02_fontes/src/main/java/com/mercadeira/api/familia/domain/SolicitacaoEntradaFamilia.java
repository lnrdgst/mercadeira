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
@Table(name = "solicitacao_entrada_familia")
public class SolicitacaoEntradaFamilia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_usuario_id", nullable = false)
    private Usuario solicitanteUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacaoEntradaFamilia status;

    @Column(name = "solicitada_em", nullable = false)
    private Instant solicitadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolvida_por_membro_familia_id")
    private MembroFamilia resolvidaPorMembroFamilia;

    @Column(name = "resolvida_em")
    private Instant resolvidaEm;

    protected SolicitacaoEntradaFamilia() {
    }

    public static SolicitacaoEntradaFamilia criar(Familia familia, Usuario solicitanteUsuario, Instant agora) {
        SolicitacaoEntradaFamilia solicitacao = new SolicitacaoEntradaFamilia();
        solicitacao.familia = familia;
        solicitacao.solicitanteUsuario = solicitanteUsuario;
        solicitacao.status = StatusSolicitacaoEntradaFamilia.PENDENTE;
        solicitacao.solicitadaEm = agora;
        return solicitacao;
    }

    public void registrarResolucao(
            StatusSolicitacaoEntradaFamilia status,
            MembroFamilia responsavel,
            Instant agora) {
        this.status = status;
        this.resolvidaPorMembroFamilia = responsavel;
        this.resolvidaEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public Familia getFamilia() {
        return familia;
    }

    public Usuario getSolicitanteUsuario() {
        return solicitanteUsuario;
    }

    public StatusSolicitacaoEntradaFamilia getStatus() {
        return status;
    }

    public MembroFamilia getResolvidaPorMembroFamilia() {
        return resolvidaPorMembroFamilia;
    }
}
