package com.mercadeira.api.compra.application;

import java.math.BigDecimal;

public record AdicionarRegistroFinanceiroCompraCommand(BigDecimal valor, String estabelecimentoNome) {
}
