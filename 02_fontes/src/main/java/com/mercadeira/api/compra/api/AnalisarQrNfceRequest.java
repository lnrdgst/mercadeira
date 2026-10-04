package com.mercadeira.api.compra.api;
import jakarta.validation.constraints.NotBlank;
public record AnalisarQrNfceRequest(@NotBlank String conteudoQr) {}
