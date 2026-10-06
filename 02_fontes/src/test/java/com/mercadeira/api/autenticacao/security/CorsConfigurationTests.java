package com.mercadeira.api.autenticacao.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigurationTests {

    private final JwtConfiguration jwtConfiguration = new JwtConfiguration();

    @Test
    void permiteMultiplasOrigensConfiguradasPorVariavelDeAmbiente() {
        CorsConfiguration configuracao = configuracaoPara(
                "https://mercadeira-dsv.vercel.app, https://mercadeira.example.com");

        assertThat(configuracao.checkOrigin("https://mercadeira-dsv.vercel.app"))
                .isEqualTo("https://mercadeira-dsv.vercel.app");
        assertThat(configuracao.checkOrigin("https://mercadeira.example.com"))
                .isEqualTo("https://mercadeira.example.com");
    }

    @Test
    void naoPermiteOrigemNaoConfigurada() {
        CorsConfiguration configuracao = configuracaoPara("https://mercadeira-dsv.vercel.app");

        assertThat(configuracao.checkOrigin("https://origem-nao-autorizada.example.com")).isNull();
    }

    @Test
    void configuraPreflightComMetodosEHeadersNecessarios() {
        CorsConfiguration configuracao = configuracaoPara("https://mercadeira-dsv.vercel.app");

        assertThat(configuracao.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuracao.getAllowedHeaders())
                .contains("Authorization", "Content-Type");
    }

    private CorsConfiguration configuracaoPara(String origins) {
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) jwtConfiguration
                .corsConfigurationSource(origins);
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/familias"));
    }
}
