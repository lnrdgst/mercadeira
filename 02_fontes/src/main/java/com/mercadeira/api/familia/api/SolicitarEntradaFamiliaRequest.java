package com.mercadeira.api.familia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarEntradaFamiliaRequest(@NotBlank @Size(max = 32) String codigoIngresso) {
}
