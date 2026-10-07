package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record AtualizarDadosItemCompraRequest(
        @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal precoUnitario,
        @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantidadeComprada) { }
