package com.mercadeira.api.compra.leiturapreco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientOpenAiResponsesClientTest {
    private MockRestServiceServer server;
    private RestClientOpenAiResponsesClient client;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientOpenAiResponsesClient(builder.build());
    }

    @Test
    void usaResponsesApiComImagemEStructuredOutput() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer chave-de-teste"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"model":"modelo-visao","input":[{"role":"user","content":[
                          {"type":"input_text"},
                          {"type":"input_image","image_url":"data:image/png;base64,AQI="}
                        ]}],"text":{"format":{"type":"json_schema","name":"precos_monetarios","strict":true,
                        "schema":{"type":"object","required":["precos"]}}}}
                        """, false))
                .andRespond(withSuccess("{\"output_text\":\"{\\\"precos\\\":[2.5]}\"}", MediaType.APPLICATION_JSON));

        assertThat(client.identificarPrecos("chave-de-teste", "modelo-visao", new byte[] { 1, 2 }, "image/png"))
                .isEqualTo("{\"precos\":[2.5]}");
        server.verify();
    }

    @Test
    void converteFalhaHttpEmErroControladoDoProvedor() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withServerError());

        assertThatThrownBy(() -> client.identificarPrecos("chave-de-teste", "modelo", new byte[] { 1 }, "image/png"))
                .isInstanceOf(LeituraPrecoIaProvedorException.class)
                .hasMessageNotContaining("chave-de-teste");
    }
}
