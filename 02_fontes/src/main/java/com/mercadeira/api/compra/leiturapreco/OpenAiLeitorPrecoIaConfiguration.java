package com.mercadeira.api.compra.leiturapreco;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class OpenAiLeitorPrecoIaConfiguration {
    @Bean
    @Qualifier("openAiPriceReaderRestClient")
    RestClient openAiPriceReaderRestClient(LeitorPrecoIaProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(requestFactory).build();
    }
}
