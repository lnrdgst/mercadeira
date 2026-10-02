package com.mercadeira.api;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.mercadeira.api.autenticacao.application.PasswordResetProperties;
import com.mercadeira.api.autenticacao.email.ResendProperties;

@SpringBootApplication
@EnableConfigurationProperties({ PasswordResetProperties.class, ResendProperties.class })
public class Application {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
