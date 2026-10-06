package com.mercadeira.api.autenticacao.google;

public class CredencialGoogleInvalidaException extends RuntimeException {
    public CredencialGoogleInvalidaException() { super("Credencial Google invalida."); }
    public CredencialGoogleInvalidaException(Throwable cause) { super("Credencial Google invalida.", cause); }
}
