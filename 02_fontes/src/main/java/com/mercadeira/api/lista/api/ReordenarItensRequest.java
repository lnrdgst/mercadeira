package com.mercadeira.api.lista.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReordenarItensRequest(@NotNull List<UUID> itens) { }
