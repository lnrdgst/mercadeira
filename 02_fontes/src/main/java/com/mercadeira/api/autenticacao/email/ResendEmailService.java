package com.mercadeira.api.autenticacao.email;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "mercadeira.email.provider", havingValue = "resend")
public class ResendEmailService implements EmailService {
    private final RestClient restClient;
    private final ResendProperties properties;
    private final String remetente;

    public ResendEmailService(@Qualifier("resendRestClient") RestClient restClient,
            ResendProperties properties, @Value("${mercadeira.mail.from:}") String remetente) {
        this.restClient = restClient;
        this.properties = properties;
        this.remetente = remetente;
    }

    @Override
    public void enviarRedefinicaoSenha(String destinatario, String link, Duration validade) {
        validarConfiguracao();
        ResendResponse resposta;
        try {
            resposta = restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new ResendRequest(remetente, List.of(destinatario),
                            "Redefini\u00e7\u00e3o de senha \u2014 Mercadeira", corpo(link, validade)))
                    .retrieve()
                    .body(ResendResponse.class);
        } catch (RestClientException exception) {
            throw new MailSendException("Nao foi possivel enviar o e-mail de redefinicao de senha.", exception);
        }
        if (resposta == null || !StringUtils.hasText(resposta.id())) {
            throw new MailSendException("O provedor de e-mail retornou uma resposta inesperada.");
        }
    }

    private void validarConfiguracao() {
        if (!StringUtils.hasText(remetente) || !StringUtils.hasText(properties.getApiUrl())
                || !StringUtils.hasText(properties.getApiKey()) || properties.getHttpTimeout().isNegative()
                || properties.getHttpTimeout().isZero()) {
            throw new IllegalStateException("Configuracao Resend incompleta.");
        }
    }

    private String corpo(String link, Duration validade) {
        return "Foi solicitada uma redefinicao de senha para sua conta.\n\n"
                + "Redefinir senha: " + link + "\n\nEste link e valido por " + validade.toMinutes()
                + " minutos e pode ser usado uma unica vez. Se voce nao fez esta solicitacao, ignore este e-mail.";
    }

    private record ResendRequest(String from, List<String> to, String subject, String text) { }
    private record ResendResponse(String id) { }
}
