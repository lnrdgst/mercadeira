package com.mercadeira.api.autenticacao.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadeira.google")
public class GoogleProperties {
    private String clientId = "";
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
