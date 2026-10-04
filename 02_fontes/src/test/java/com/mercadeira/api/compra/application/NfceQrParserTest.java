package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class NfceQrParserTest {

    private final NfceQrParser parser = new NfceQrParser();
    private static final String CHAVE = "31261012345678000123650010000012341000012345";

    @Test
    void naoReconheceConteudoVazioOuQrNaoFiscal() {
        assertThat(parser.analisar(" ").nfceReconhecida()).isFalse();
        assertThat(parser.analisar("https://mercadeira.app/convite/123").nfceReconhecida()).isFalse();
    }

    @Test
    void extraiDadosPresentesSemConsultarUrlExterna() {
        var analise = parser.analisar("https://sefaz.exemplo.gov.br/nfce?chave=" + CHAVE
                + "&valor=37,53&cnpj=12.345.678%2F0001-90&emitente=Mercado%20Central");

        assertThat(analise.nfceReconhecida()).isTrue();
        assertThat(analise.chaveNfce()).isEqualTo(CHAVE);
        assertThat(analise.urlConsulta()).contains("sefaz.exemplo.gov.br");
        assertThat(analise.valor()).isEqualByComparingTo(new BigDecimal("37.53"));
        assertThat(analise.cnpjEmitente()).isEqualTo("12345678000190");
        assertThat(analise.estabelecimentoNome()).isEqualTo("Mercado Central");
    }

    @Test
    void aceitaNfceParcialSemValorOuEstabelecimento() {
        var analise = parser.analisar("https://sefaz.exemplo.gov.br/nfce?q=" + CHAVE);

        assertThat(analise.nfceReconhecida()).isTrue();
        assertThat(analise.chaveNfce()).isEqualTo(CHAVE);
        assertThat(analise.valor()).isNull();
        assertThat(analise.estabelecimentoNome()).isNull();
    }

    @Test
    void reconheceFormatoDeMgComParametroPLiteralSemDadosFinanceiros() {
        String url = "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + CHAVE + "|2|1|1|hash-de-teste";

        var analise = parser.analisar(url);

        assertThat(analise.nfceReconhecida()).isTrue();
        assertThat(analise.chaveNfce()).isEqualTo(CHAVE);
        assertThat(analise.urlConsulta()).isEqualTo(url);
        assertThat(analise.valor()).isNull();
        assertThat(analise.estabelecimentoNome()).isNull();
    }

    @Test
    void reconheceFormatoDeMgComSeparadoresCodificados() {
        var analise = parser.analisar("https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + CHAVE + "%7C2%7C1%7C1%7Chash-de-teste");

        assertThat(analise.nfceReconhecida()).isTrue();
        assertThat(analise.chaveNfce()).isEqualTo(CHAVE);
    }

    @Test
    void naoReconheceParametroPInvalidoOuChaveQueNaoEDeNfce() {
        assertThat(parser.analisar("https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=").nfceReconhecida()).isFalse();
        assertThat(parser.analisar("https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p=123|2|1").nfceReconhecida()).isFalse();
        assertThat(parser.analisar("https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml?p="
                + "31261012345678000123550010000012341000012345|2|1").nfceReconhecida()).isFalse();
    }

    @Test
    void qrMalformadoNaoInterrompeFluxo() {
        assertThat(parser.analisar("https://[invalido").nfceReconhecida()).isFalse();
        assertThat(parser.analisar("nota-fiscal:" + CHAVE).nfceReconhecida()).isFalse();
    }
}
