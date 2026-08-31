package com.mercadeira.api.lista.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
@Table(name = "item_lista")
public class ItemLista {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_compra_id", nullable = false)
    private ListaCompra listaCompra;

    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @Column(name = "quantidade", precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", length = 30)
    private UnidadeMedida unidadeMedida;

    @Column(name = "marca", length = 120)
    private String marca;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "ordem_exibicao", nullable = false)
    private Integer ordemExibicao;

    @Column(name = "removido_em")
    private Instant removidoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adicionado_por_membro_familia_id", nullable = false)
    private MembroFamilia adicionadoPorMembroFamilia;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ItemLista() {
    }

    public static ItemLista criar(
            ListaCompra listaCompra,
            String descricao,
            BigDecimal quantidade,
            UnidadeMedida unidadeMedida,
            String marca,
            String observacoes,
            Integer ordemExibicao,
            MembroFamilia adicionadoPorMembroFamilia,
            Instant agora) {
        ItemLista item = new ItemLista();
        item.listaCompra = listaCompra;
        item.descricao = descricao;
        item.quantidade = quantidade;
        item.unidadeMedida = unidadeMedida;
        item.marca = marca;
        item.observacoes = observacoes;
        item.ordemExibicao = ordemExibicao;
        item.adicionadoPorMembroFamilia = adicionadoPorMembroFamilia;
        item.criadoEm = agora;
        item.atualizadoEm = agora;
        return item;
    }

    public void editar(String descricao, BigDecimal quantidade, UnidadeMedida unidadeMedida,
            String marca, String observacoes, Instant agora) {
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.unidadeMedida = unidadeMedida;
        this.marca = marca;
        this.observacoes = observacoes;
        this.atualizadoEm = agora;
    }

    public void remover(Instant agora) {
        removidoEm = agora;
        atualizadoEm = agora;
    }

    public void definirOrdemExibicao(int ordemExibicao, Instant agora) {
        this.ordemExibicao = ordemExibicao;
        this.atualizadoEm = agora;
    }

    public UUID getId() { return id; }
    public ListaCompra getListaCompra() { return listaCompra; }
    public String getDescricao() { return descricao; }
    public BigDecimal getQuantidade() { return quantidade; }
    public UnidadeMedida getUnidadeMedida() { return unidadeMedida; }
    public String getMarca() { return marca; }
    public String getObservacoes() { return observacoes; }
    public Integer getOrdemExibicao() { return ordemExibicao; }
    public Instant getRemovidoEm() { return removidoEm; }
    public MembroFamilia getAdicionadoPorMembroFamilia() { return adicionadoPorMembroFamilia; }
}
