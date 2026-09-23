package com.mercadeira.api.lista.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReaproveitarItensForaCompraRequest(@NotEmpty List<@NotNull UUID> itemIds) {}
