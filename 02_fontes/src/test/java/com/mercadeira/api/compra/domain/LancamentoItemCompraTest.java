package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.lang.reflect.Field;
import java.util.UUID;

import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.familia.domain.MembroFamilia;
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

    @Test
    void editaERemoveLancamentoRecalculandoTotal() throws Exception {
        var item = item();
        var lancamento = item.adicionarLancamento(new BigDecimal("4.90"), BigDecimal.ONE, AGORA);
        UUID id = UUID.randomUUID();
        definirId(lancamento, id);

        item.editarLancamento(id, new BigDecimal("5.00"), new BigDecimal("2"), AGORA.plusSeconds(1));
        assertThat(lancamento.getSubtotal()).isEqualByComparingTo("10.00");
        assertThat(item.getValorTotal()).isEqualByComparingTo("10.00");

        item.removerLancamento(id);
        assertThat(item.getLancamentos()).isEmpty();
        assertThat(item.getValorTotal()).isNull();
    }

    @Test
    void naoEditaNemRemoveLancamentoDeOutroItem() {
        UUID idDeOutroItem = UUID.randomUUID();
        assertThatThrownBy(() -> item().editarLancamento(idDeOutroItem, BigDecimal.ONE, BigDecimal.ONE, AGORA))
                .isInstanceOf(LancamentoItemCompraNaoEncontradoException.class);
        assertThatThrownBy(() -> item().removerLancamento(idDeOutroItem))
                .isInstanceOf(LancamentoItemCompraNaoEncontradoException.class);
    }

    private static void assertInvalido(BigDecimal preco, BigDecimal quantidade) {
        assertThatThrownBy(() -> item().adicionarLancamento(preco, quantidade, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ItemCompra item() {
        ItemCompra item = ItemCompra.criarDuranteCompra(null, null, "Produto", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 1, AGORA);
        item.colocarNoCarrinho(org.mockito.Mockito.mock(MembroFamilia.class), AGORA);
        return item;
    }

    private static void definirId(LancamentoItemCompra lancamento, UUID id) throws Exception {
        Field campo = LancamentoItemCompra.class.getDeclaredField("id");
        campo.setAccessible(true);
        campo.set(lancamento, id);
    }
}
