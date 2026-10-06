package com.mercadeira.api.lista.api;

import java.math.BigDecimal;

public record ResumoFinanceiroCompraResponse(
        BigDecimal totalRegistrado,
        long quantidadeRegistrosFinanceiros,
        long quantidadeEstabelecimentos,
        String estabelecimentoResumo) {
}
