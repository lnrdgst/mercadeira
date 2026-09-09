package com.mercadeira.api.compra.application;

import com.mercadeira.api.compra.domain.ItemCompra;

public record ResultadoDecisaoRemocaoItemCompra(ItemCompra item, boolean decisaoAplicada) {
}
