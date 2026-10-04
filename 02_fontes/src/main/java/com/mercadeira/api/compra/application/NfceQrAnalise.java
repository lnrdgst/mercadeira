package com.mercadeira.api.compra.application;

import java.math.BigDecimal;
import java.time.Instant;

public record NfceQrAnalise(boolean nfceReconhecida, String chaveNfce, String urlConsulta,
        BigDecimal valor, String estabelecimentoNome, String cnpjEmitente, Instant dataHoraDocumento) {

    public static NfceQrAnalise naoReconhecida() {
        return new NfceQrAnalise(false, null, null, null, null, null, null);
    }
}
