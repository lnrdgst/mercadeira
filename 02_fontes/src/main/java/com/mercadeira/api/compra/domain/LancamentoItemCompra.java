package com.mercadeira.api.compra.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

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
@Table(name = "lancamento_item_compra")
public class LancamentoItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_compra_id", nullable = false)
    private ItemCompra itemCompra;

    @Column(name = "preco_unitario", nullable = false, precision = 19, scale = 4)
    private BigDecimal precoUnitario;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected LancamentoItemCompra() {
    }

    static LancamentoItemCompra criar(ItemCompra itemCompra, BigDecimal precoUnitario,
            BigDecimal quantidade, Instant criadoEm) {
        validar(itemCompra, precoUnitario, quantidade, criadoEm);
        LancamentoItemCompra lancamento = new LancamentoItemCompra();
        lancamento.itemCompra = itemCompra;
        lancamento.precoUnitario = precoUnitario;
        lancamento.quantidade = quantidade;
        lancamento.criadoEm = criadoEm;
        lancamento.atualizadoEm = criadoEm;
        return lancamento;
    }

    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(quantidade).setScale(2, RoundingMode.HALF_UP);
    }

    private static void validar(ItemCompra itemCompra, BigDecimal precoUnitario,
            BigDecimal quantidade, Instant criadoEm) {
        if (itemCompra == null || precoUnitario == null || quantidade == null || criadoEm == null
                || precoUnitario.signum() <= 0 || quantidade.signum() <= 0) {
            throw new IllegalArgumentException("Lancamento financeiro invalido.");
        }
    }

    public UUID getId() { return id; }
    public ItemCompra getItemCompra() { return itemCompra; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public BigDecimal getQuantidade() { return quantidade; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
