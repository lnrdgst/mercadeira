package com.mercadeira.api.autenticacao.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RedefinirSenhaRequest(@NotBlank @Size(max = 512) String token, @NotBlank @Size(max = 255) String novaSenha) { }
