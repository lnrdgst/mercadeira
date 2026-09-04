package com.mercadeira.api.compra.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ItemLista;
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
}
