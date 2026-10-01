package com.mercadeira.api.autenticacao.email;

import java.time.Duration;

public interface EmailService {
    void enviarRedefinicaoSenha(String destinatario, String link, Duration validade);
}
