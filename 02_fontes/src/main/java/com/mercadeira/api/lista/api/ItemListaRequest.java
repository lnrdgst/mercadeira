package com.mercadeira.api.lista.api;

import java.math.BigDecimal;

import com.mercadeira.api.lista.domain.UnidadeMedida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemListaRequest(@NotBlank @Size(max = 200) String descricao, BigDecimal quantidade,
        UnidadeMedida unidadeMedida, @Size(max = 120) String marca, String observacoes) { }
