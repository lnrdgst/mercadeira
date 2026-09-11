package com.mercadeira.api.compra.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_compra")
public class ItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_lista_origem_id", unique = true)
    private ItemLista itemListaOrigem;

    @Column(name = "adicionado_durante_compra", nullable = false)
    private boolean adicionadoDuranteCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adicionado_por_participante_compra_id")
    private ParticipanteCompra adicionadoPorParticipanteCompra;

    @Column(name = "adicionado_em")
    private Instant adicionadoEm;

    @Column(name = "ordem_exibicao", nullable = false)
    private Integer ordemExibicao;

    @Column(name = "descricao_snapshot", nullable = false, length = 200)
    private String descricaoSnapshot;

    @Column(name = "quantidade_snapshot", precision = 12, scale = 3)
    private BigDecimal quantidadeSnapshot;

    @Column(name = "unidade_medida_snapshot", length = 30)
    private String unidadeMedidaSnapshot;

    @Column(name = "marca_snapshot", length = 120)
    private String marcaSnapshot;

    @Column(name = "observacoes_snapshot", columnDefinition = "TEXT")
    private String observacoesSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusItemCompra status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marcado_por_membro_familia_id")
    private MembroFamilia marcadoPorMembroFamilia;

    @Column(name = "marcado_em")
    private Instant marcadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "decisao_remocao", length = 20)
    private DecisaoRemocao decisaoRemocao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remocao_solicitada_por_membro_familia_id")
    private MembroFamilia remocaoSolicitadaPorMembroFamilia;

    @Column(name = "remocao_solicitada_em")
    private Instant remocaoSolicitadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remocao_resolvida_por_membro_familia_id")
    private MembroFamilia remocaoResolvidaPorMembroFamilia;

    @Column(name = "remocao_resolvida_em")
    private Instant remocaoResolvidaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurado_por_participante_compra_id")
    private ParticipanteCompra restauradoPorParticipanteCompra;

    @Column(name = "restaurado_em")
    private Instant restauradoEm;

    protected ItemCompra() {
    }

    public static ItemCompra criarDaPreparacao(Compra compra, ItemLista origem) {
        ItemCompra item = new ItemCompra();
        item.compra = compra;
        item.itemListaOrigem = origem;
        item.adicionadoDuranteCompra = false;
        item.ordemExibicao = origem.getOrdemExibicao();
        item.descricaoSnapshot = origem.getDescricao();
        item.quantidadeSnapshot = origem.getQuantidade();
        item.unidadeMedidaSnapshot = origem.getUnidadeMedida() == null ? null : origem.getUnidadeMedida().name();
        item.marcaSnapshot = origem.getMarca();
        item.observacoesSnapshot = origem.getObservacoes();
        item.status = StatusItemCompra.PENDENTE;
        return item;
    }

    public static ItemCompra criarDuranteCompra(
            Compra compra,
            ParticipanteCompra autor,
            String descricao,
            BigDecimal quantidade,
            UnidadeMedida unidadeMedida,
            String marca,
            String observacoes,
            Integer ordemExibicao,
            Instant adicionadoEm) {
        ItemCompra item = new ItemCompra();
        item.compra = compra;
        item.adicionadoDuranteCompra = true;
        item.adicionadoPorParticipanteCompra = autor;
        item.adicionadoEm = adicionadoEm;
        item.ordemExibicao = ordemExibicao;
        item.descricaoSnapshot = descricao;
        item.quantidadeSnapshot = quantidade;
        item.unidadeMedidaSnapshot = unidadeMedida == null ? null : unidadeMedida.name();
        item.marcaSnapshot = marca;
        item.observacoesSnapshot = observacoes;
        item.status = StatusItemCompra.PENDENTE;
        return item;
    }

    public boolean colocarNoCarrinho(MembroFamilia executor, Instant instante) {
        if (status == StatusItemCompra.NO_CARRINHO) {
            return false;
        }
        if (status != StatusItemCompra.PENDENTE) {
            throw new TransicaoStatusItemCompraInvalidaException(status);
        }
        status = StatusItemCompra.NO_CARRINHO;
        marcadoPorMembroFamilia = executor;
        marcadoEm = instante;
        return true;
    }

    public boolean solicitarRemocao(MembroFamilia solicitante, Instant instante) {
        if (status == StatusItemCompra.REMOCAO_SOLICITADA) {
            return false;
        }
        if (status != StatusItemCompra.NO_CARRINHO) {
            throw new TransicaoStatusItemCompraInvalidaException(status);
        }
        remocaoSolicitadaPorMembroFamilia = solicitante;
        remocaoSolicitadaEm = instante;
        decisaoRemocao = null;
        remocaoResolvidaPorMembroFamilia = null;
        remocaoResolvidaEm = null;
        status = StatusItemCompra.REMOCAO_SOLICITADA;
        return true;
    }

    public boolean aprovarRemocao(MembroFamilia decisor, Instant instante) {
        if (status == StatusItemCompra.REMOVIDO && decisaoRemocao == DecisaoRemocao.APROVADA) {
            return false;
        }
        if (status != StatusItemCompra.REMOCAO_SOLICITADA) {
            throw new TransicaoStatusItemCompraInvalidaException(status);
        }
        decisaoRemocao = DecisaoRemocao.APROVADA;
        remocaoResolvidaPorMembroFamilia = decisor;
        remocaoResolvidaEm = instante;
        status = StatusItemCompra.REMOVIDO;
        return true;
    }

    public boolean rejeitarRemocao(MembroFamilia decisor, Instant instante) {
        if (status == StatusItemCompra.NO_CARRINHO && decisaoRemocao == DecisaoRemocao.REJEITADA) {
            return false;
        }
        if (status != StatusItemCompra.REMOCAO_SOLICITADA) {
            throw new TransicaoStatusItemCompraInvalidaException(status);
        }
        decisaoRemocao = DecisaoRemocao.REJEITADA;
        remocaoResolvidaPorMembroFamilia = decisor;
        remocaoResolvidaEm = instante;
        status = StatusItemCompra.NO_CARRINHO;
        return true;
    }

    public ParticipanteCompra getRestauradoPorParticipanteCompra() { return restauradoPorParticipanteCompra; }
    public Instant getRestauradoEm() { return restauradoEm; }
    public UUID getId() { return id; }
    public Compra getCompra() { return compra; }
    public ItemLista getItemListaOrigem() { return itemListaOrigem; }
    public boolean isAdicionadoDuranteCompra() { return adicionadoDuranteCompra; }
    public ParticipanteCompra getAdicionadoPorParticipanteCompra() { return adicionadoPorParticipanteCompra; }
    public Instant getAdicionadoEm() { return adicionadoEm; }
    public Integer getOrdemExibicao() { return ordemExibicao; }
    public String getDescricaoSnapshot() { return descricaoSnapshot; }
    public BigDecimal getQuantidadeSnapshot() { return quantidadeSnapshot; }
    public String getUnidadeMedidaSnapshot() { return unidadeMedidaSnapshot; }
    public String getMarcaSnapshot() { return marcaSnapshot; }
    public String getObservacoesSnapshot() { return observacoesSnapshot; }
    public StatusItemCompra getStatus() { return status; }
    public MembroFamilia getMarcadoPorMembroFamilia() { return marcadoPorMembroFamilia; }
    public Instant getMarcadoEm() { return marcadoEm; }
    public DecisaoRemocao getDecisaoRemocao() { return decisaoRemocao; }
    public MembroFamilia getRemocaoSolicitadaPorMembroFamilia() { return remocaoSolicitadaPorMembroFamilia; }
    public Instant getRemocaoSolicitadaEm() { return remocaoSolicitadaEm; }
    public MembroFamilia getRemocaoResolvidaPorMembroFamilia() { return remocaoResolvidaPorMembroFamilia; }
    public Instant getRemocaoResolvidaEm() { return remocaoResolvidaEm; }
}
