package com.mercadeira.api.compra.api;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record TransferirResponsabilidadeRequest(@NotNull UUID participanteCompraId) {}
