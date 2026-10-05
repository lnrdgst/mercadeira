package com.mercadeira.api.autenticacao.google;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
class GoogleJwtConfiguration {
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    @Qualifier("googleJwtDecoder")
    JwtDecoder googleJwtDecoder(GoogleProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), NimbusGoogleCredentialValidator.validatorFor(properties.getClientId())));
        return decoder;
    }
}
