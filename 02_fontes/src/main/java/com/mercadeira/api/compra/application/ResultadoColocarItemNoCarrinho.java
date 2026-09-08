package com.mercadeira.api.compra.application;

import com.mercadeira.api.compra.domain.ItemCompra;

public record ResultadoColocarItemNoCarrinho(ItemCompra item, boolean alterado) {
}
