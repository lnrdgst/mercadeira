package com.mercadeira.api.compra.application;

import com.mercadeira.api.compra.domain.Compra;

public record ResultadoFinalizacaoCompra(Compra compra, boolean finalizadaAgora) {}