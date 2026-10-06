package com.mercadeira.api.compra.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import com.mercadeira.api.compra.domain.TipoRegistroFinanceiroCompra;

public record RegistroFinanceiroCompraResponse(UUID id, BigDecimal valor, TipoRegistroFinanceiroCompra tipo,
        String estabelecimentoNome, String chaveNfce, String urlConsulta, String cnpjEmitente,
        Instant dataHoraDocumento, Instant criadoEm) {

    static RegistroFinanceiroCompraResponse from(RegistroFinanceiroCompra registro) {
        return new RegistroFinanceiroCompraResponse(registro.getId(), registro.getValor(), registro.getTipo(),
                registro.getEstabelecimentoNome(), registro.getChaveNfce(), registro.getUrlConsulta(),
                registro.getCnpjEmitente(), registro.getDataHoraDocumento(), registro.getCriadoEm());
    }
}
