package com.mercadeira.api.autenticacao.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadeira.session")
public class SessionProperties {
    private int expirationDays = 180;

    public int getExpirationDays() { return expirationDays; }
    public void setExpirationDays(int expirationDays) { this.expirationDays = expirationDays; }
}
