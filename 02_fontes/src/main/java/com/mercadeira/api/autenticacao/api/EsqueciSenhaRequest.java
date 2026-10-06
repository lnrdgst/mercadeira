package com.mercadeira.api.autenticacao.api;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record EsqueciSenhaRequest(@NotBlank @Email @Size(max = 255) String email) {
    public EsqueciSenhaRequest {
        email = email == null ? null : email.trim();
    }
}
