package com.mercadeira.api.compra.application;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
public class AutoridadePresencaException extends RuntimeException {
    public AutoridadePresencaException() { super("O participante nao pode executar esta acao de presenca."); }
}
