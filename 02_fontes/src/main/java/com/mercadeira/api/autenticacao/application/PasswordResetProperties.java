package com.mercadeira.api.autenticacao.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mercadeira.password-reset")
public class PasswordResetProperties {
    private String frontendUrl = "http://localhost:3000";
    private long expirationMinutes = 30;
    private long cooldownSeconds = 60;
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
    public long getExpirationMinutes() { return expirationMinutes; }
    public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
}
