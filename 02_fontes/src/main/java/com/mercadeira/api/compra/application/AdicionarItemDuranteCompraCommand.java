package com.mercadeira.api.compra.application;

import java.math.BigDecimal;

import com.mercadeira.api.lista.domain.UnidadeMedida;

public record AdicionarItemDuranteCompraCommand(
        String descricao,
        BigDecimal quantidade,
        UnidadeMedida unidadeMedida,
        String marca,
        String observacoes) {
}
