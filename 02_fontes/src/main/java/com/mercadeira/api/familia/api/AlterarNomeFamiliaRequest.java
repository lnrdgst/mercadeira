package com.mercadeira.api.familia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarNomeFamiliaRequest(@NotBlank @Size(max = 120) String nome) {
}
