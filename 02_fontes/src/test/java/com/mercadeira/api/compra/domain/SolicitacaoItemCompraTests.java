package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import org.junit.jupiter.api.Test;

class SolicitacaoItemCompraTests {
    private final Instant agora = Instant.parse("2026-10-11T12:00:00Z");
    private final Compra compra = mock(Compra.class);
    private final ParticipanteCompra participante = mock(ParticipanteCompra.class);

    private SolicitacaoItemCompra pedido() {
        when(compra.getId()).thenReturn(UUID.randomUUID());
        when(participante.getId()).thenReturn(UUID.randomUUID());
        when(participante.getCompra()).thenReturn(compra);
        return SolicitacaoItemCompra.criar(compra, participante, "Leite", new BigDecimal("1.500"), UnidadeMedida.LITRO, agora);
    }

    @Test
    void nascePendenteComDadosOperacionaisEQuantidadeDecimal() {
        var s = pedido();
        assertThat(s.getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
        assertThat(s.getCompraId()).isEqualTo(compra.getId());
        assertThat(s.getSolicitanteId()).isEqualTo(participante.getId());
        assertThat(s.getDescricao()).isEqualTo("Leite");
        assertThat(s.getQuantidade()).isEqualByComparingTo("1.5");
        assertThat(s.getUnidadeMedida()).isEqualTo(UnidadeMedida.LITRO);
        assertThat(s.getCriadoEm()).isEqualTo(agora);
        assertThat(s.getDecididoEm()).isNull();
        assertThat(s.getDecididoPorId()).isNull();
    }

    @Test
    void aprovaUmaUnicaVezERegistraDecisorEInstante() {
        var s = pedido();
        s.aprovar(participante, agora.plusSeconds(1));
        assertThat(s.getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.APROVADA);
        assertThat(s.getDecididoEm()).isEqualTo(agora.plusSeconds(1));
        assertThat(s.getDecididoPorId()).isEqualTo(participante.getId());
        assertThatThrownBy(() -> s.aprovar(participante, agora.plusSeconds(2)))
                .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
        assertThatThrownBy(() -> s.recusar(participante, agora.plusSeconds(2)))
                .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
    }

    @Test
    void recusaUmaUnicaVez() {
        var s = pedido();
        s.recusar(participante, agora.plusSeconds(1));
        assertThat(s.getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.RECUSADA);
        assertThat(s.getDecididoPorId()).isEqualTo(participante.getId());
        assertThat(s.getDecididoEm()).isEqualTo(agora.plusSeconds(1));
        assertThatThrownBy(() -> s.aprovar(participante, agora.plusSeconds(2)))
                .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
        assertThatThrownBy(() -> s.recusar(participante, agora.plusSeconds(2)))
                .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
    }

    @Test
    void rejeitaDecisorDeOutraCompraOuInstanteInvalidoSemMutacao() {
        var s = pedido();
        var outro = mock(ParticipanteCompra.class);
        var outraCompra = mock(Compra.class);
        when(outro.getId()).thenReturn(UUID.randomUUID());
        when(outro.getCompra()).thenReturn(outraCompra);
        when(outraCompra.getId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> s.aprovar(outro, agora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.recusar(participante, agora.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.recusar(null, agora)).isInstanceOf(IllegalArgumentException.class);
        assertThat(s.getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
        assertThat(s.getDecididoEm()).isNull();
    }

    @Test
    void rejeitaDescricaoEQuantidadeInvalidasEAceitaOpcionaisNulos() {
        pedido();
        for (String descricao : new String[] {"", " ", "a".repeat(201)}) {
            assertThatThrownBy(() -> SolicitacaoItemCompra.criar(compra, participante, descricao, null, null, agora))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (BigDecimal quantidade : new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            assertThatThrownBy(() -> SolicitacaoItemCompra.criar(compra, participante, "Leite", quantidade, null, agora))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var s = SolicitacaoItemCompra.criar(compra, participante, "Leite", null, null, agora);
        assertThat(s.getQuantidade()).isNull();
        assertThat(s.getUnidadeMedida()).isNull();
    }
}
