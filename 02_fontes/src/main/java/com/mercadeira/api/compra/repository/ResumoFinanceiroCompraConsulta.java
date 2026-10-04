package com.mercadeira.api.compra.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record ResumoFinanceiroCompraConsulta(
        UUID referenciaId,
        BigDecimal totalRegistrado,
        long quantidadeRegistrosFinanceiros,
        long quantidadeEstabelecimentos,
        String estabelecimentoResumo) {
}
