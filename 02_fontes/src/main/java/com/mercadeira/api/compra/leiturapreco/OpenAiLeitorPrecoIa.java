package com.mercadeira.api.compra.leiturapreco;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiLeitorPrecoIa implements LeitorPrecoIa {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiLeitorPrecoIa.class);
    private static final int MAXIMO_CANDIDATOS = 20;

    private final LeitorPrecoIaProperties properties;
    private final OpenAiResponsesClient client;
    private final ObjectMapper objectMapper;

    OpenAiLeitorPrecoIa(LeitorPrecoIaProperties properties, OpenAiResponsesClient client, ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BigDecimal> identificarPrecos(byte[] imagem, String contentType) {
        validarDisponibilidade(imagem, contentType);
        long inicio = System.nanoTime();
        logger.info("Leitura de preco por IA iniciada: modelo={}", properties.getModel());
        try {
            String resposta = client.identificarPrecos(properties.getApiKey(), properties.getModel(), imagem, contentType);
            List<BigDecimal> precos = normalizarResposta(resposta);
            logger.info("Leitura de preco por IA concluida: modelo={}, duracaoMs={}, quantidadePrecos={}", properties.getModel(),
                    Duration.ofNanos(System.nanoTime() - inicio).toMillis(), precos.size());
            return precos;
        } catch (LeituraPrecoIaTimeoutException exception) {
            logger.warn("Leitura de preco por IA excedeu o tempo limite: modelo={}, duracaoMs={}", properties.getModel(),
                    Duration.ofNanos(System.nanoTime() - inicio).toMillis());
            throw exception;
        } catch (LeituraPrecoIaException exception) {
            logger.warn("Leitura de preco por IA falhou: modelo={}, tipo={}", properties.getModel(), exception.getClass().getSimpleName());
            throw exception;
        } catch (RuntimeException exception) {
            logger.warn("Leitura de preco por IA falhou: modelo={}, tipo={}", properties.getModel(), exception.getClass().getSimpleName());
            throw new LeituraPrecoIaProvedorException("Falha inesperada na leitura de preco.", exception);
        }
    }

    private void validarDisponibilidade(byte[] imagem, String contentType) {
        if (!properties.isEnabled()) throw new LeituraPrecoIaIndisponivelException("A leitura de preco por IA esta desabilitada.");
        if (!StringUtils.hasText(properties.getApiKey())) throw new LeituraPrecoIaIndisponivelException("A chave da leitura de preco por IA nao esta configurada.");
        if (!StringUtils.hasText(properties.getModel())) throw new LeituraPrecoIaIndisponivelException("O modelo da leitura de preco por IA nao esta configurado.");
        if (imagem == null || imagem.length == 0 || !StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new LeituraPrecoIaRespostaInvalidaException("A imagem informada para leitura de preco e invalida.");
        }
    }

    private List<BigDecimal> normalizarResposta(String resposta) {
        try {
            RespostaPrecos respostaPrecos = objectMapper.readValue(resposta, RespostaPrecos.class);
            if (respostaPrecos == null || respostaPrecos.precos() == null) {
                throw new LeituraPrecoIaRespostaInvalidaException("O provedor retornou resposta estruturada invalida.");
            }
            LinkedHashSet<BigDecimal> normalizados = new LinkedHashSet<>();
            for (BigDecimal preco : respostaPrecos.precos()) {
                if (preco == null || preco.signum() <= 0) continue;
                try {
                    normalizados.add(preco.setScale(2, RoundingMode.UNNECESSARY));
                } catch (ArithmeticException ignored) {
                    // Valores que nao representam moeda com duas casas exatas sao descartados.
                }
                if (normalizados.size() >= MAXIMO_CANDIDATOS) break;
            }
            return List.copyOf(normalizados);
        } catch (LeituraPrecoIaRespostaInvalidaException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LeituraPrecoIaRespostaInvalidaException("O provedor retornou resposta estruturada invalida.", exception);
        }
    }

    private record RespostaPrecos(List<BigDecimal> precos) { }
}
