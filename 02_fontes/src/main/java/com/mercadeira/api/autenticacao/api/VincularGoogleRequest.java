package com.mercadeira.api.autenticacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VincularGoogleRequest(@NotBlank @Size(max = 8192) String credential,
        @NotBlank @Size(max = 255) String senhaAtual) { }
