package com.mercadeira.api.autenticacao.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class EmailProviderSelectionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderConfiguration.class)
            .withPropertyValues(
                    "mercadeira.mail.from=no-reply@example.test",
                    "spring.mail.host=localhost",
                    "mercadeira.resend.api-url=https://resend.example.test/emails",
                    "mercadeira.resend.api-key=re_test_key");

    @Test
    void selecionaSomenteEmailServiceSmtp() {
        contextRunner.withPropertyValues("mercadeira.email.provider=smtp").run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context.getBean(EmailService.class)).isInstanceOf(SmtpEmailService.class);
        });
    }

    @Test
    void selecionaSomenteEmailServiceResend() {
        contextRunner.withPropertyValues("mercadeira.email.provider=resend").run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context.getBean(EmailService.class)).isInstanceOf(ResendEmailService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ResendProperties.class)
    @Import({ MailConfiguration.class, SmtpEmailService.class, ResendConfiguration.class, ResendEmailService.class })
    static class ProviderConfiguration { }
}
