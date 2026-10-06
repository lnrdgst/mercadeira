package com.mercadeira.api.familia.api;

import jakarta.validation.constraints.NotNull;

public record PermissaoIniciarCompraRequest(@NotNull Boolean podeIniciarCompra) { }
