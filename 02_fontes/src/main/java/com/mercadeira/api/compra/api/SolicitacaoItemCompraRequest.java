package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitacaoItemCompraRequest(@NotBlank @Size(max = 200) String descricao,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantidade,
        UnidadeMedida unidadeMedida) {}
