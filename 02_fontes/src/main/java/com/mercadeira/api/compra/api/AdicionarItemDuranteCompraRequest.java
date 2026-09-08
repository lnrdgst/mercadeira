package com.mercadeira.api.compra.api;

import java.math.BigDecimal;

import com.mercadeira.api.lista.domain.UnidadeMedida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdicionarItemDuranteCompraRequest(
        @NotBlank(message = "A descricao e obrigatoria.")
        @Size(max = 200)
        String descricao,
        BigDecimal quantidade,
        UnidadeMedida unidadeMedida,
        @Size(max = 120)
        String marca,
        String observacoes) {
}
