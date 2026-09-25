package com.mercadeira.api.familia.application;
public class ExclusaoFamiliaInvalidaException extends RuntimeException { public ExclusaoFamiliaInvalidaException() { super("Não é possível excluir esta família."); } }
