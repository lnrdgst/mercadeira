package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.mercadeira.api.lista.domain.UnidadeMedida;
import org.junit.jupiter.api.Test;

class LancamentoItemCompraTest {

    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void criaLancamentoValidoECalculaSubtotal() {
        var lancamento = item().adicionarLancamento(new BigDecimal("4.90"), BigDecimal.ONE, AGORA);

        assertThat(lancamento.getPrecoUnitario()).isEqualByComparingTo("4.90");
        assertThat(lancamento.getQuantidade()).isEqualByComparingTo("1");
        assertThat(lancamento.getCriadoEm()).isEqualTo(AGORA);
        assertThat(lancamento.getAtualizadoEm()).isEqualTo(AGORA);
        assertThat(lancamento.getSubtotal()).isEqualByComparingTo("4.90");
    }

    @Test
    void rejeitaPrecoOuQuantidadeAusentesZeroOuNegativos() {
        assertInvalido(null, BigDecimal.ONE);
        assertInvalido(BigDecimal.ONE, null);
        assertInvalido(BigDecimal.ZERO, BigDecimal.ONE);
        assertInvalido(BigDecimal.ONE, BigDecimal.ZERO);
        assertInvalido(new BigDecimal("-1"), BigDecimal.ONE);
        assertInvalido(BigDecimal.ONE, new BigDecimal("-1"));
    }

    @Test
    void arredondaSubtotalComHalfUp() {
        var lancamento = item().adicionarLancamento(new BigDecimal("0.3333"), new BigDecimal("3"), AGORA);

        assertThat(lancamento.getSubtotal()).isEqualByComparingTo("1.00");
    }

    @Test
    void itemSemLancamentosTemTotalDerivadoZero() {
        assertThat(item().getValorTotalDosLancamentos()).isEqualByComparingTo("0.00");
    }

    @Test
    void somaSubtotaisDeMultiplosLancamentos() {
        var item = item();
        item.adicionarLancamento(new BigDecimal("4.90"), BigDecimal.ONE, AGORA);
        item.adicionarLancamento(new BigDecimal("5.00"), new BigDecimal("2"), AGORA.plusSeconds(1));

        assertThat(item.getLancamentos()).hasSize(2);
        assertThat(item.getValorTotalDosLancamentos()).isEqualByComparingTo("14.90");
        assertThat(item.getValorTotal()).isEqualByComparingTo("14.90");
    }

    private static void assertInvalido(BigDecimal preco, BigDecimal quantidade) {
        assertThatThrownBy(() -> item().adicionarLancamento(preco, quantidade, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ItemCompra item() {
        return ItemCompra.criarDuranteCompra(null, null, "Produto", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 1, AGORA);
    }
}
