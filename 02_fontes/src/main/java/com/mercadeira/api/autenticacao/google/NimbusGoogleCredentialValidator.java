package com.mercadeira.api.autenticacao.google;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
class NimbusGoogleCredentialValidator implements GoogleCredentialValidator {
    private final JwtDecoder decoder;
    private final GoogleProperties properties;

    NimbusGoogleCredentialValidator(@Qualifier("googleJwtDecoder") JwtDecoder googleJwtDecoder, GoogleProperties properties) {
        this.decoder = googleJwtDecoder;
        this.properties = properties;
    }

    @Override
    public GoogleIdentity validar(String credential) {
        if (!StringUtils.hasText(properties.getClientId()) || !StringUtils.hasText(credential)) {
            throw new CredencialGoogleInvalidaException();
        }
        try {
            Jwt jwt = decoder.decode(credential);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            if (!StringUtils.hasText(subject) || !StringUtils.hasText(email) || !Boolean.TRUE.equals(emailVerified)) {
                throw new CredencialGoogleInvalidaException();
            }
            return new GoogleIdentity(subject, email, jwt.getClaimAsString("name"));
        } catch (CredencialGoogleInvalidaException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CredencialGoogleInvalidaException(exception);
        }
    }

    static OAuth2TokenValidator<Jwt> validatorFor(String clientId) {
        return jwt -> {
            boolean issuerValido = jwt.getIssuer() != null
                    && List.of("https://accounts.google.com", "accounts.google.com").contains(jwt.getIssuer().toString());
            boolean audienceValida = jwt.getAudience() != null && jwt.getAudience().contains(clientId);
            return issuerValido && audienceValida
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
    }
}
