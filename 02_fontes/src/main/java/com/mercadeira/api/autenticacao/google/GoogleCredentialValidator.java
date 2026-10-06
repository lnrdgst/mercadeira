package com.mercadeira.api.autenticacao.google;

public interface GoogleCredentialValidator {
    GoogleIdentity validar(String credential);
}
