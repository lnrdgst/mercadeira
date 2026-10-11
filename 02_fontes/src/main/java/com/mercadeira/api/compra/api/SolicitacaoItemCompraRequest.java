package com.mercadeira.api.compra.api;
import java.math.BigDecimal; import com.mercadeira.api.lista.domain.UnidadeMedida; import jakarta.validation.constraints.*;
public record SolicitacaoItemCompraRequest(@NotBlank @Size(max=200) String descricao, @DecimalMin(value="0.001") BigDecimal quantidade, UnidadeMedida unidadeMedida) {}
