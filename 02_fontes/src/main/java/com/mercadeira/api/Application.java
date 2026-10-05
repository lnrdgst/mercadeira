package com.mercadeira.api;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.mercadeira.api.autenticacao.application.PasswordResetProperties;
import com.mercadeira.api.autenticacao.application.SessionProperties;
import com.mercadeira.api.autenticacao.email.ResendProperties;
import com.mercadeira.api.compra.application.AlertaContinuidadeCompraProperties;

@SpringBootApplication(exclude = MailSenderAutoConfiguration.class)
@EnableConfigurationProperties({ PasswordResetProperties.class, ResendProperties.class, SessionProperties.class, AlertaContinuidadeCompraProperties.class })
public class Application {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
