package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import org.junit.jupiter.api.Test;

class ItemCompraValoresTest {
    @Test
    void itemNoCarrinhoPodeFicarSemPreco() {
        ItemCompra item = noCarrinho();
        item.registrarDadosDaCompra(null, null);
        assertThat(item.getValorTotal()).isNull();
    }

    @Test
    void calculaValorComPrecisaoERecalculaQuantidade() {
        ItemCompra item = noCarrinho();
        item.registrarDadosDaCompra(new BigDecimal("27.00"), new BigDecimal("2"));
        assertThat(item.getValorTotal()).isEqualByComparingTo("54.00");
        item.registrarDadosDaCompra(new BigDecimal("27.00"), BigDecimal.ONE);
        assertThat(item.getValorTotal()).isEqualByComparingTo("27.00");
    }

    @Test
    void calculaDezUnidadesDeOitentaENoveCentavos() {
        ItemCompra item = noCarrinho();
        item.registrarDadosDaCompra(new BigDecimal("0.89"), new BigDecimal("10"));
        assertThat(item.getValorTotal()).isEqualByComparingTo("8.90");
    }

    @Test
    void permiteQuantidadeSemPrecoMasExigeQuantidadeQuandoPrecoExiste() {
        ItemCompra item = noCarrinho();
        item.registrarDadosDaCompra(null, new BigDecimal("2"));
        assertThat(item.getValorTotal()).isNull();
        assertThatThrownBy(() -> item.registrarDadosDaCompra(new BigDecimal("27.00"), null))
                .isInstanceOf(DadosFinanceirosItemCompraInvalidosException.class);
        assertThatThrownBy(() -> item.registrarDadosDaCompra(new BigDecimal("0"), BigDecimal.ONE))
                .isInstanceOf(DadosFinanceirosItemCompraInvalidosException.class);
    }

    private static ItemCompra noCarrinho() {
        ItemCompra item = ItemCompra.criarDuranteCompra(null, null, "Arroz", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 1, Instant.parse("2026-10-06T12:00:00Z"));
        item.colocarNoCarrinho(mock(MembroFamilia.class), Instant.parse("2026-10-06T12:01:00Z"));
        return item;
    }
}
