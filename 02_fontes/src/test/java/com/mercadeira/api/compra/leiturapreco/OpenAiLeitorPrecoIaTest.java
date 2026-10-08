package com.mercadeira.api.compra.leiturapreco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

class OpenAiLeitorPrecoIaTest {
    private final LeitorPrecoIaProperties properties = new LeitorPrecoIaProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void configurar() {
        properties.setEnabled(true);
        properties.setApiKey("sk_nao_registrar");
        properties.setModel("modelo-de-teste");
        logger = (Logger) LoggerFactory.getLogger(OpenAiLeitorPrecoIa.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void limparLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void naoUsaClienteQuandoFeatureEstaDesligadaMesmoSemChave() {
        properties.setEnabled(false);
        properties.setApiKey(null);
        AtomicBoolean chamado = new AtomicBoolean();
        OpenAiLeitorPrecoIa leitor = leitor((key, model, imagem, tipo) -> {
            chamado.set(true);
            return "{\"precos\":[]}";
        });

        assertThatThrownBy(() -> leitor.identificarPrecos(new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaIndisponivelException.class)
                .hasMessageContaining("desabilitada");
        assertThat(chamado).isFalse();
    }

    @Test
    void falhaDeFormaControladaQuandoFeatureAtivaNaoTemChave() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> leitor(resposta("{\"precos\":[]}")).identificarPrecos(new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaIndisponivelException.class)
                .hasMessageContaining("chave");
    }

    @Test
    void retornaUmPrecoNormalizado() {
        assertThat(leitor(resposta("{\"precos\":[2.5]}")).identificarPrecos(new byte[] { 1 }, "image/png"))
                .containsExactly(new BigDecimal("2.50"));
    }

    @Test
    void retornaMultiplosPrecosPreservandoOrdem() {
        assertThat(leitor(resposta("{\"precos\":[13.98,14.9,15.98]}")).identificarPrecos(new byte[] { 1 }, "image/jpeg"))
                .containsExactly(new BigDecimal("13.98"), new BigDecimal("14.90"), new BigDecimal("15.98"));
    }

    @Test
    void aceitaListaVaziaComoLeituraSemCandidato() {
        assertThat(leitor(resposta("{\"precos\":[]}")).identificarPrecos(new byte[] { 1 }, "image/png")).isEmpty();
    }

    @Test
    void descartaDuplicadosValoresNaoPositivosENaoMonetarios() {
        assertThat(leitor(resposta("{\"precos\":[2.5,2.50,0,-1,12.345,3.000]}")).identificarPrecos(new byte[] { 1 }, "image/png"))
                .containsExactly(new BigDecimal("2.50"), new BigDecimal("3.00"));
    }

    @Test
    void limitaQuantidadeDeCandidatos() {
        String precos = java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(i -> i + ".00")
                .collect(java.util.stream.Collectors.joining(","));

        assertThat(leitor(resposta("{\"precos\":[" + precos + "]}")).identificarPrecos(new byte[] { 1 }, "image/png")).hasSize(20);
    }

    @Test
    void rejeitaRespostaEstruturadaInvalida() {
        assertThatThrownBy(() -> leitor(resposta("{\"outro\":[]}")).identificarPrecos(new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaRespostaInvalidaException.class);
    }

    @Test
    void distingueTimeout() {
        OpenAiResponsesClient client = (key, model, imagem, tipo) -> {
            throw new LeituraPrecoIaTimeoutException("tempo", new SocketTimeoutException());
        };

        assertThatThrownBy(() -> leitor(client).identificarPrecos(new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaTimeoutException.class);
    }

    @Test
    void distingueFalhaDoProvedor() {
        OpenAiResponsesClient client = (key, model, imagem, tipo) -> {
            throw new LeituraPrecoIaProvedorException("falha", new IllegalStateException());
        };

        assertThatThrownBy(() -> leitor(client).identificarPrecos(new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaProvedorException.class);
    }

    @Test
    void nuncaRegistraChaveOuConteudoDaImagemNosLogs() {
        byte[] imagem = "imagem-confidencial".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        leitor(resposta("{\"precos\":[2.5]}")).identificarPrecos(imagem, "image/png");

        String mensagens = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(mensagens).doesNotContain("sk_nao_registrar", "imagem-confidencial", "{\"precos\"");
    }

    private OpenAiLeitorPrecoIa leitor(OpenAiResponsesClient client) {
        return new OpenAiLeitorPrecoIa(properties, client, objectMapper);
    }

    private OpenAiResponsesClient resposta(String texto) {
        return (key, model, imagem, tipo) -> texto;
    }
}
