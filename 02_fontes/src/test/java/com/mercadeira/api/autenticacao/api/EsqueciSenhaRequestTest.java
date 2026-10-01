package com.mercadeira.api.autenticacao.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EsqueciSenhaRequestTest {
    @Test
    void removeEspacosAntesDaValidacaoDoEmail() {
        assertThat(new EsqueciSenhaRequest("  ANA@example.test  ").email()).isEqualTo("ANA@example.test");
    }
}
