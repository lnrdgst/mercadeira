package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import com.mercadeira.api.familia.domain.MembroFamilia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RestauracaoItemCompraTest {
    private static final Instant T = Instant.parse("2026-09-11T20:00:00Z");

    @Test
    void restauraComUmInstanteEReplayNaoSubstituiAutoria() {
        var item=removido(); var primeiro=participante(item.getCompra()); var segundo=participante(item.getCompra());
        var solicitante=item.getRemocaoSolicitadaPorMembroFamilia();
        var decisor=item.getRemocaoResolvidaPorMembroFamilia();
        assertThat(item.restaurarNoCarrinho(primeiro,T)).isTrue();
        assertThat(item.getRestauradoEm()).isSameAs(T);
        assertThat(item.getMarcadoEm()).isSameAs(T);
        assertThat(item.getMarcadoPorMembroFamilia()).isSameAs(primeiro.getMembroFamilia());
        assertThat(item.getRemocaoSolicitadaPorMembroFamilia()).isSameAs(solicitante);
        assertThat(item.getRemocaoResolvidaPorMembroFamilia()).isSameAs(decisor);
        assertThat(item.getRemocaoSolicitadaEm()).isEqualTo(T.minusSeconds(2));
        assertThat(item.getRemocaoResolvidaEm()).isEqualTo(T.minusSeconds(1));
        assertThat(item.getDecisaoRemocao()).isEqualTo(DecisaoRemocao.APROVADA);
        assertThat(item.restaurarNoCarrinho(segundo,T.plusSeconds(1))).isFalse();
        assertThat(item.getRestauradoPorParticipanteCompra()).isSameAs(primeiro);
        assertThat(item.getMarcadoEm()).isSameAs(T);
    }

    @ParameterizedTest
    @ValueSource(strings={"decisaoRemocao","remocaoSolicitadaPorMembroFamilia","remocaoSolicitadaEm",
            "remocaoResolvidaPorMembroFamilia","remocaoResolvidaEm","marcadoPorMembroFamilia","marcadoEm"})
    void removidoComAuditoriaIncompletaRejeita(String campo) {
        var item=removido(); ReflectionTestUtils.setField(item,campo,null);
        assertThatThrownBy(() -> item.restaurarNoCarrinho(participante(item.getCompra()),T))
                .isInstanceOf(RestauracaoItemCompraInvalidaException.class);
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.REMOVIDO);
        assertThat(item.getRestauradoEm()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings={"restauradoPorParticipanteCompra","restauradoEm","marcadoPorMembroFamilia","marcadoEm"})
    void replayIncompletoRejeita(String campo) {
        var item=removido(); item.restaurarNoCarrinho(participante(item.getCompra()),T);
        ReflectionTestUtils.setField(item,campo,null);
        assertThatThrownBy(() -> item.restaurarNoCarrinho(participante(item.getCompra()),T.plusSeconds(1)))
                .isInstanceOf(RestauracaoItemCompraInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings={"tempo","marcador","compra"})
    void replayNaoAceitaRestauracaoIncoerente(String caso) {
        var item=removido(); item.restaurarNoCarrinho(participante(item.getCompra()),T);
        if(caso.equals("tempo")) ReflectionTestUtils.setField(item,"restauradoEm",T.minusSeconds(3));
        if(caso.equals("marcador")) ReflectionTestUtils.setField(item,"marcadoPorMembroFamilia",membro());
        if(caso.equals("compra")) ReflectionTestUtils.setField(item,"restauradoPorParticipanteCompra",participante(compra()));
        assertThatThrownBy(() -> item.restaurarNoCarrinho(participante(item.getCompra()),T.plusSeconds(1)))
                .isInstanceOf(RestauracaoItemCompraInvalidaException.class);
    }

    @Test
    void rejeitaRestauradorEstrangeiroETempoAnteriorAResolucao() {
        var item=removido();
        assertThatThrownBy(() -> item.restaurarNoCarrinho(participante(compra()),T))
                .isInstanceOf(RestauracaoItemCompraInvalidaException.class);
        assertThatThrownBy(() -> item.restaurarNoCarrinho(participante(item.getCompra()),T.minusSeconds(2)))
                .isInstanceOf(RestauracaoItemCompraInvalidaException.class);
        assertThat(item.getRestauradoEm()).isNull();
    }

    private ItemCompra removido() {
        var item=BeanUtils.instantiateClass(ItemCompra.class);
        ReflectionTestUtils.setField(item,"compra",compra());
        ReflectionTestUtils.setField(item,"status",StatusItemCompra.PENDENTE);
        var responsavel=membro();
        item.colocarNoCarrinho(responsavel,T.minusSeconds(3));
        item.solicitarRemocao(membro(),T.minusSeconds(2));
        item.aprovarRemocao(responsavel,T.minusSeconds(1));
        return item;
    }
    private Compra compra() {var c=mock(Compra.class); when(c.getId()).thenReturn(UUID.randomUUID()); return c;}
    private MembroFamilia membro() {var m=mock(MembroFamilia.class); when(m.getId()).thenReturn(UUID.randomUUID()); return m;}
    private ParticipanteCompra participante(Compra compra) {
        var p=mock(ParticipanteCompra.class); var membro=membro(); when(p.getCompra()).thenReturn(compra); when(p.getMembroFamilia()).thenReturn(membro); return p;
    }
}
