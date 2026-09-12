package com.mercadeira.api.compra.application;
import com.mercadeira.api.compra.domain.ItemCompra;
public record ResultadoRestauracaoItemCompra(ItemCompra item, boolean restauradoAgora) {}