package com.mercadeira.api.compra.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.domain.TransicaoStatusListaCompraInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class FinalizacaoCompraTest {
    private static final Instant INSTANTE = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void primeiraFinalizacaoEReplayPreservamAutorEInstante() {
        var compra = compra(StatusCompra.EM_ANDAMENTO);
        var primeiro = participante(compra);
        var segundo = participante(compra);
        assertThat(compra.finalizar(primeiro, INSTANTE)).isTrue();
        assertThat(compra.finalizar(segundo, INSTANTE.plusSeconds(60))).isFalse();
        assertThat(compra.getStatus()).isEqualTo(StatusCompra.FINALIZADA);
        assertThat(compra.getFinalizadaPorParticipanteCompra()).isSameAs(primeiro);
        assertThat(compra.getFinalizadaEm()).isEqualTo(INSTANTE);
    }

    @ParameterizedTest
    @CsvSource({"FINALIZADA,false,false", "FINALIZADA,true,false", "FINALIZADA,false,true",
            "CANCELADA,false,false", "EM_ANDAMENTO,true,true"})
    void rejeitaEstadoOuAuditoriaIncompativel(String status, boolean autor, boolean instante) {
        var compra = compra(StatusCompra.valueOf(status));
        ReflectionTestUtils.setField(compra, "finalizadaPorParticipanteCompra", autor ? participante(compra) : null);
        ReflectionTestUtils.setField(compra, "finalizadaEm", instante ? INSTANTE : null);
        assertThatThrownBy(() -> compra.finalizar(participante(compra), INSTANTE))
                .isInstanceOf(FinalizacaoCompraInvalidaException.class);
        assertThat(compra.getStatus()).isEqualTo(StatusCompra.valueOf(status));
    }

    @Test
    void autorDeOutraCompraNaoPodeFinalizarNemRepresentarReplay() {
        var compra = compra(StatusCompra.EM_ANDAMENTO);
        var estrangeiro = participante(compra(StatusCompra.EM_ANDAMENTO));
        assertThatThrownBy(() -> compra.finalizar(estrangeiro, INSTANTE))
                .isInstanceOf(FinalizacaoCompraInvalidaException.class);
        ReflectionTestUtils.setField(compra, "status", StatusCompra.FINALIZADA);
        ReflectionTestUtils.setField(compra, "finalizadaPorParticipanteCompra", estrangeiro);
        ReflectionTestUtils.setField(compra, "finalizadaEm", INSTANTE);
        assertThatThrownBy(() -> compra.finalizar(participante(compra), INSTANTE))
                .isInstanceOf(FinalizacaoCompraInvalidaException.class);
    }

    @Test
    void listaFinalizaComMesmoInstanteSemSetterGenerico() {
        var lista = BeanUtils.instantiateClass(ListaCompra.class);
        ReflectionTestUtils.setField(lista, "status", StatusListaCompra.EM_COMPRA);
        lista.finalizarCompra(INSTANTE);
        assertThat(lista.getStatus()).isEqualTo(StatusListaCompra.FINALIZADA);
        assertThat(lista.getAtualizadaEm()).isEqualTo(INSTANTE);
        assertThatThrownBy(() -> lista.finalizarCompra(INSTANTE.plusSeconds(60)))
                .isInstanceOf(TransicaoStatusListaCompraInvalidaException.class);
        assertThat(lista.getAtualizadaEm()).isEqualTo(INSTANTE);
    }

    private Compra compra(StatusCompra status) {
        var compra = BeanUtils.instantiateClass(Compra.class);
        ReflectionTestUtils.setField(compra, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(compra, "status", status);
        return compra;
    }

    private ParticipanteCompra participante(Compra compra) {
        var participante = mock(ParticipanteCompra.class);
        when(participante.getCompra()).thenReturn(compra);
        return participante;
    }
}
