package com.mercadeira.api.compra.application;

import com.mercadeira.api.compra.domain.ItemCompra;

public record ResultadoSolicitarRemocaoItemCompra(
        ItemCompra item,
        boolean solicitacaoCriada,
        boolean removidoAutomaticamente) {
}
