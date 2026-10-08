package com.mercadeira.api.compra.leiturapreco;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mercadeira.ai-price-reader")
public class LeitorPrecoIaProperties {
    private boolean enabled;
    private String apiKey;
    private String model;
    private int timeoutSeconds = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
