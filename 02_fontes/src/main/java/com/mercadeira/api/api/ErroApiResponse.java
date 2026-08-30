package com.mercadeira.api.api;

import java.time.Instant;
import java.util.Map;

public record ErroApiResponse(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        String path,
        Map<String, String> campos) {
}
