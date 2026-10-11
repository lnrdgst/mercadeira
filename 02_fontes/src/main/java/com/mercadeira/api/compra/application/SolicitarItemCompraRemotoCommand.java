package com.mercadeira.api.compra.application;
import java.math.BigDecimal;
import com.mercadeira.api.lista.domain.UnidadeMedida;
public record SolicitarItemCompraRemotoCommand(String descricao, BigDecimal quantidade, UnidadeMedida unidadeMedida) {}
