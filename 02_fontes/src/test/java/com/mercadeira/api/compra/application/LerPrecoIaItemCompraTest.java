package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.leiturapreco.LeitorPrecoIa;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LerPrecoIaItemCompraTest {
    @Test
    void validaMesmaPermissaoDaEdicaoAntesDeEntregarSomenteBytesEContentTypeAoLeitor() {
        RegistrarDadosItemCompra autorizacao = mock(RegistrarDadosItemCompra.class);
        LeitorPrecoIa leitor = mock(LeitorPrecoIa.class);
        LerPrecoIaItemCompra casoDeUso = new LerPrecoIaItemCompra(autorizacao, leitor);
        UUID usuario = UUID.randomUUID(), familia = UUID.randomUUID(), lista = UUID.randomUUID(), item = UUID.randomUUID();
        byte[] imagem = { 1, 2, 3 };
        when(leitor.identificarPrecos(imagem, "image/png")).thenReturn(List.of(new BigDecimal("2.50")));

        assertThat(casoDeUso.executar(usuario, familia, lista, item, imagem, "image/png"))
                .containsExactly(new BigDecimal("2.50"));

        verify(autorizacao).validarEdicao(usuario, familia, lista, item);
        verify(leitor).identificarPrecos(imagem, "image/png");
    }
}
