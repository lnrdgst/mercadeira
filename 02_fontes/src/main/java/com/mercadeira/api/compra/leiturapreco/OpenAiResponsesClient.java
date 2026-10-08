package com.mercadeira.api.compra.leiturapreco;

interface OpenAiResponsesClient {
    String identificarPrecos(String apiKey, String model, byte[] imagem, String contentType);
}
