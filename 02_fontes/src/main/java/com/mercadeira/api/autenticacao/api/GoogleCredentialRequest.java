package com.mercadeira.api.autenticacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleCredentialRequest(@NotBlank @Size(max = 8192) String credential) { }
