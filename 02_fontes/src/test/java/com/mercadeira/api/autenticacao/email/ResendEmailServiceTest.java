package com.mercadeira.api.autenticacao.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailServiceTest {
    private MockRestServiceServer server;
    private ResendEmailService service;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ResendProperties properties = new ResendProperties();
        properties.setApiUrl("https://resend.example.test/emails");
        properties.setApiKey("re_test_key");
        service = new ResendEmailService(builder.build(), properties, "Mercadeira <onboarding@resend.dev>");
        logger = (Logger) LoggerFactory.getLogger(ResendEmailService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void removerAppender() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void enviaRequisicaoAutenticadaComConteudoDaRedefinicao() {
        server.expect(once(), requestTo("https://resend.example.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"from":"Mercadeira <onboarding@resend.dev>","to":["ana@example.test"],
                         "subject":"Redefini\u00e7\u00e3o de senha \u2014 Mercadeira",
                         "text":"Foi solicitada uma redefinicao de senha para sua conta.\n\nRedefinir senha: https://app.example.test/redefinir-senha?token=segredo\n\nEste link e valido por 30 minutos e pode ser usado uma unica vez. Se voce nao fez esta solicitacao, ignore este e-mail."}
                        """))
                .andRespond(withSuccess("{\"id\":\"email-123\"}", MediaType.APPLICATION_JSON));

        service.enviarRedefinicaoSenha("ana@example.test", "https://app.example.test/redefinir-senha?token=segredo",
                Duration.ofMinutes(30));

        server.verify();
    }

    @Test
    void falhaQuandoRespostaNaoContemId() {
        server.expect(requestTo("https://resend.example.test/emails"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.enviarRedefinicaoSenha("ana@example.test", "https://app.example.test/link",
                Duration.ofMinutes(30))).isInstanceOf(MailSendException.class);
    }

    @Test
    void registraErroHttpEstruturadoSemVazarCredenciaisOuConteudoDoEmail() {
        server.expect(requestTo("https://resend.example.test/emails"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"name":"validation_error","type":"invalid_parameter","message":"O remetente nao foi verificado."}
                                """));

        assertThatThrownBy(() -> service.enviarRedefinicaoSenha("ana@example.test",
                "https://app.example.test/redefinir-senha?token=segredo",
                Duration.ofMinutes(30))).isInstanceOf(MailSendException.class);

        String mensagem = logs.list.getFirst().getFormattedMessage();
        assertThat(mensagem).contains("status=400", "name=validation_error", "type=invalid_parameter",
                "message=O remetente nao foi verificado.");
        assertThat(mensagem).doesNotContain("re_test_key", "segredo", "Foi solicitada uma redefinicao de senha");
    }

    @Test
    void rejeitaConfiguracaoObrigatoriaAusente() {
        ResendProperties properties = new ResendProperties();
        properties.setApiUrl("https://resend.example.test/emails");
        service = new ResendEmailService(RestClient.builder().build(), properties, "no-reply@example.test");

        assertThatThrownBy(() -> service.enviarRedefinicaoSenha("ana@example.test", "https://app.example.test/link",
                Duration.ofMinutes(30))).isInstanceOf(IllegalStateException.class);
    }
}
