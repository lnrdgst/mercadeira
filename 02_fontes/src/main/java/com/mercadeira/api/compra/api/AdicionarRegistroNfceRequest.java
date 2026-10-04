package com.mercadeira.api.compra.api;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record AdicionarRegistroNfceRequest(@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal valor,
    @Size(max=120) String estabelecimentoNome, @Pattern(regexp="\\d{44}") String chaveNfce,
    @Pattern(regexp="https?://.+") @Size(max=1000) String urlConsulta,
    @Pattern(regexp="\\d{14}") String cnpjEmitente) {}
