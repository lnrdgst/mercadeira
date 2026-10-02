package com.mercadeira.api.autenticacao.email;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mercadeira.resend")
public class ResendProperties {
    private String apiUrl;
    private String apiKey;
    private Duration httpTimeout = Duration.ofSeconds(10);

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Duration getHttpTimeout() { return httpTimeout; }
    public void setHttpTimeout(Duration httpTimeout) { this.httpTimeout = httpTimeout; }
}
