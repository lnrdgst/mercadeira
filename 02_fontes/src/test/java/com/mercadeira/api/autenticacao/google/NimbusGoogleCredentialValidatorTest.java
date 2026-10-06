package com.mercadeira.api.autenticacao.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class NimbusGoogleCredentialValidatorTest {
    @Test void aceitaSomenteClaimsObrigatoriasDepoisDaValidacaoCriptograficaDoDecoder() {
        JwtDecoder decoder = mock(JwtDecoder.class); GoogleProperties properties = new GoogleProperties(); properties.setClientId("client-id");
        when(decoder.decode("credential")).thenReturn(jwt("sub-1", "ana@example.test", true));
        GoogleIdentity identity = new NimbusGoogleCredentialValidator(decoder, properties).validar("credential");
        assertThat(identity).isEqualTo(new GoogleIdentity("sub-1", "ana@example.test", null));
    }

    @Test void rejeitaEmailNaoVerificadoEErroDoDecoderSemExporCredential() {
        JwtDecoder decoder = mock(JwtDecoder.class); GoogleProperties properties = new GoogleProperties(); properties.setClientId("client-id");
        when(decoder.decode("nao-verificado")).thenReturn(jwt("sub-1", "ana@example.test", false));
        when(decoder.decode("invalido")).thenThrow(new IllegalArgumentException("token invalido"));
        NimbusGoogleCredentialValidator validator = new NimbusGoogleCredentialValidator(decoder, properties);
        assertThatThrownBy(() -> validator.validar("nao-verificado")).isInstanceOf(CredencialGoogleInvalidaException.class);
        assertThatThrownBy(() -> validator.validar("invalido")).isInstanceOf(CredencialGoogleInvalidaException.class);
    }

    @Test void validatorExigeIssuerEAudienceGoogleConfigurado() {
        assertThat(NimbusGoogleCredentialValidator.validatorFor("client-id").validate(jwt("sub", "a@b.test", true)).hasErrors()).isTrue();
        Jwt jwtValido = Jwt.withTokenValue("token").header("alg", "RS256").issuer("https://accounts.google.com")
                .audience(List.of("client-id")).subject("sub").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "ana@example.test").claim("email_verified", true).build();
        assertThat(NimbusGoogleCredentialValidator.validatorFor("client-id").validate(jwtValido).hasErrors()).isFalse();
    }

    private static Jwt jwt(String subject, String email, boolean verified) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claims(c -> c.putAll(Map.of("email", email, "email_verified", verified))).build();
    }
}
