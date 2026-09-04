package com.mercadeira.api.lista.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
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
@Table(name = "lista_compra")
public class ListaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 100)
    private CategoriaCompra categoria;

    @Column(name = "estabelecimento", length = 120)
    private String estabelecimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusListaCompra status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criada_por_membro_familia_id", nullable = false)
    private MembroFamilia criadaPorMembroFamilia;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected ListaCompra() {
    }

    public static ListaCompra criar(
            Familia familia,
            String nome,
            CategoriaCompra categoria,
            String estabelecimento,
            MembroFamilia criadaPorMembroFamilia,
            Instant agora) {
        ListaCompra lista = new ListaCompra();
        lista.familia = familia;
        lista.nome = nome;
        lista.categoria = categoria;
        lista.estabelecimento = estabelecimento;
        lista.status = StatusListaCompra.EM_PREPARACAO;
        lista.criadaPorMembroFamilia = criadaPorMembroFamilia;
        lista.criadaEm = agora;
        lista.atualizadaEm = agora;
        return lista;
    }

    public void registrarAtualizacao(Instant agora) {
        atualizadaEm = agora;
    }

    public void iniciarCompra(Instant agora) {
        if (status != StatusListaCompra.EM_PREPARACAO) {
            throw new TransicaoStatusListaCompraInvalidaException(status, StatusListaCompra.EM_COMPRA);
        }
        status = StatusListaCompra.EM_COMPRA;
        atualizadaEm = agora;
    }

    public UUID getId() { return id; }
    public Familia getFamilia() { return familia; }
    public String getNome() { return nome; }
    public CategoriaCompra getCategoria() { return categoria; }
    public String getEstabelecimento() { return estabelecimento; }
    public StatusListaCompra getStatus() { return status; }
    public MembroFamilia getCriadaPorMembroFamilia() { return criadaPorMembroFamilia; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
}
