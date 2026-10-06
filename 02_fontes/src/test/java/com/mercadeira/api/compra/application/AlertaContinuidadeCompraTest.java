package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class AlertaContinuidadeCompraTest {
    private static final Instant AGORA = Instant.parse("2026-10-05T12:00:00Z");
    private final AlertaContinuidadeCompraProperties properties = properties();
    private final AlertaContinuidadeCompra alerta = new AlertaContinuidadeCompra(Clock.fixed(AGORA, ZoneOffset.UTC), properties);

    @Test
    void alertaSomenteResponsavelAposVinteEQuatroHorasESemAdiamentoValido() {
        UUID responsavel = UUID.randomUUID();
        assertThat(alerta.avaliar(compra(AGORA.minusSeconds(24 * 60 * 60 - 60), responsavel, null), responsavel).necessario()).isFalse();
        assertThat(alerta.avaliar(compra(AGORA.minusSeconds(24 * 60 * 60), responsavel, null), responsavel).necessario()).isTrue();
        assertThat(alerta.avaliar(compra(AGORA.minusSeconds(25 * 60 * 60), responsavel, AGORA.plusSeconds(1)), responsavel).necessario()).isFalse();
        assertThat(alerta.avaliar(compra(AGORA.minusSeconds(25 * 60 * 60), responsavel, AGORA), responsavel).necessario()).isTrue();
    }

    @Test
    void naoAlertaParticipanteComumNemCompraFinalizada() {
        UUID responsavel = UUID.randomUUID();
        assertThat(alerta.avaliar(compra(AGORA.minusSeconds(25 * 60 * 60), responsavel, null), UUID.randomUUID()).necessario()).isFalse();
        Compra finalizada = compra(AGORA.minusSeconds(25 * 60 * 60), responsavel, null);
        ReflectionTestUtils.setField(finalizada, "status", StatusCompra.FINALIZADA);
        assertThat(alerta.avaliar(finalizada, responsavel).necessario()).isFalse();
    }

    @Test
    void continuarApenasAdiaAlertaSemMudarEstadoOperacional() {
        UUID responsavelId = UUID.randomUUID();
        Compra compra = compra(AGORA.minusSeconds(25 * 60 * 60), responsavelId, null);
        ParticipanteCompra responsavel = mock(ParticipanteCompra.class);
        when(responsavel.getId()).thenReturn(responsavelId);

        compra.adiarAlertaContinuidade(responsavel, alerta.proximoAlerta());

        assertThat(compra.getStatus()).isEqualTo(StatusCompra.EM_ANDAMENTO);
        assertThat(compra.getAlertaContinuidadeAdiadoAte()).isEqualTo(AGORA.plusSeconds(24 * 60 * 60));
        assertThat(alerta.avaliar(compra, responsavelId).necessario()).isFalse();
        assertThatThrownBy(() -> compra.adiarAlertaContinuidade(mock(ParticipanteCompra.class), alerta.proximoAlerta()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AlertaContinuidadeCompraProperties properties() {
        AlertaContinuidadeCompraProperties properties = new AlertaContinuidadeCompraProperties();
        properties.setHoras(24);
        return properties;
    }

    private static Compra compra(Instant iniciadaEm, UUID responsavelId, Instant adiadoAte) {
        Compra compra = BeanUtils.instantiateClass(Compra.class);
        ReflectionTestUtils.setField(compra, "status", StatusCompra.EM_ANDAMENTO);
        ReflectionTestUtils.setField(compra, "iniciadaEm", iniciadaEm);
        ReflectionTestUtils.setField(compra, "responsavelOperacionalId", responsavelId);
        ReflectionTestUtils.setField(compra, "alertaContinuidadeAdiadoAte", adiadoAte);
        return compra;
    }
}
