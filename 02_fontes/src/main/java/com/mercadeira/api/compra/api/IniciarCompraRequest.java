package com.mercadeira.api.compra.api;

import java.util.List;
import java.util.UUID;

public record IniciarCompraRequest(List<UUID> participantesPresentesIds) {}
