package com.mercadeira.api.compra.leiturapreco;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class RestClientOpenAiResponsesClient implements OpenAiResponsesClient {
    private static final String INSTRUCAO = "Identifique todos os valores monetarios visiveis na imagem. "
            + "Nao identifique produto, nao escolha qual preco e correto, nao interprete atacado, varejo, clube ou promocao. "
            + "Nao devolva quantidade, codigo, percentual, peso ou volume como preco e nao infira valor ausente. "
            + "Responda somente no formato estruturado solicitado.";

    private final RestClient restClient;

    RestClientOpenAiResponsesClient(@Qualifier("openAiPriceReaderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String identificarPrecos(String apiKey, String model, byte[] imagem, String contentType) {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest(model, INSTRUCAO,
                List.of(new InputMessage("user", List.of(
                        new InputText("input_text", "Extraia os precos monetarios visiveis desta imagem."),
                        new InputImage("input_image", "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imagem), "high")))),
                new TextConfiguration(new JsonSchemaFormat("json_schema", "precos_monetarios", true,
                        Map.of("type", "object", "additionalProperties", false,
                                "properties", Map.of("precos", Map.of("type", "array", "items", Map.of("type", "number"))),
                                "required", List.of("precos")))));
        try {
            OpenAiResponsesResponse response = restClient.post().uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(request).retrieve().body(OpenAiResponsesResponse.class);
            if (response == null || response.outputText() == null) {
                throw new LeituraPrecoIaRespostaInvalidaException("O provedor retornou resposta sem conteudo estruturado.");
            }
            return response.outputText();
        } catch (LeituraPrecoIaException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (causadoPorTimeout(exception)) {
                throw new LeituraPrecoIaTimeoutException("A leitura de preco excedeu o tempo limite.", exception);
            }
            throw new LeituraPrecoIaProvedorException("Nao foi possivel conectar ao provedor de leitura de preco.", exception);
        } catch (RestClientException exception) {
            throw new LeituraPrecoIaProvedorException("O provedor de leitura de preco retornou uma falha.", exception);
        }
    }

    private boolean causadoPorTimeout(Throwable exception) {
        for (Throwable atual = exception; atual != null; atual = atual.getCause()) {
            if (atual instanceof SocketTimeoutException || atual instanceof java.util.concurrent.TimeoutException) return true;
        }
        return false;
    }

    private record OpenAiResponsesRequest(String model, String instructions, List<InputMessage> input, TextConfiguration text) { }
    private record InputMessage(String role, List<Object> content) { }
    private record InputText(String type, String text) { }
    private record InputImage(String type, String image_url, String detail) { }
    private record TextConfiguration(JsonSchemaFormat format) { }
    private record JsonSchemaFormat(String type, String name, boolean strict, Map<String, Object> schema) { }
    private record OpenAiResponsesResponse(String output_text) {
        String outputText() { return output_text; }
    }
}
