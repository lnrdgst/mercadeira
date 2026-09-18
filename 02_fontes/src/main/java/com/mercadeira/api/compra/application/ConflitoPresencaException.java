package com.mercadeira.api.compra.application;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
public class ConflitoPresencaException extends RuntimeException {
    public ConflitoPresencaException(String mensagem) { super(mensagem); }
}
