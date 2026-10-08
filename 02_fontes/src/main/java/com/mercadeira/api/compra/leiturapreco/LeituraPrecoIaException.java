package com.mercadeira.api.compra.leiturapreco;

public class LeituraPrecoIaException extends RuntimeException {
    public LeituraPrecoIaException(String message) { super(message); }
    public LeituraPrecoIaException(String message, Throwable cause) { super(message, cause); }
}
