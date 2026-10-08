package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.util.List;

public record LeituraPrecoIaResponse(List<BigDecimal> precos) { }
