package com.mercadeira.api.autenticacao.api;
public record MensagemGenericaResponse(String mensagem) {
    public MensagemGenericaResponse() { this("Se existir uma conta associada a este e-mail, enviaremos as instrucoes para redefinir sua senha."); }
}
