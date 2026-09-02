package com.mercadeira.api.lista.api;

import com.mercadeira.api.lista.domain.CategoriaCompra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListaCompraRequest(@NotBlank @Size(max = 120) String nome,
        @NotNull CategoriaCompra categoria, @Size(max = 120) String estabelecimento) { }
