package com.mercadeira.api.compra.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mercadeira.compra.alerta-continuidade")
public class AlertaContinuidadeCompraProperties {
    private long horas = 24;

    public long getHoras() { return horas; }
    public void setHoras(long horas) { this.horas = horas; }
    public Duration duracao() {
        if (horas <= 0) throw new IllegalStateException("A duracao do alerta de continuidade deve ser positiva.");
        return Duration.ofHours(horas);
    }
}
