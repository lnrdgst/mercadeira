package com.mercadeira.api.compra.application;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class NfceQrParser {
    private static final Pattern CHAVE = Pattern.compile("(?<!\\d)(\\d{44})(?!\\d)");

    public NfceQrAnalise analisar(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) return NfceQrAnalise.naoReconhecida();
        try {
            URI uri = URI.create(conteudo.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) return NfceQrAnalise.naoReconhecida();

            var chaveMatcher = CHAVE.matcher(conteudo);
            String chave = chaveMatcher.find() ? chaveMatcher.group(1) : null;
            String textoNormalizado = conteudo.toLowerCase(Locale.ROOT);
            String caminho = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            boolean nfce = caminho.contains("nfce") || textoNormalizado.contains("nfce")
                    || textoNormalizado.contains("chave") || chave != null;
            if (!nfce) return NfceQrAnalise.naoReconhecida();

            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            String cnpj = extrair(query, "cnpj");
            return new NfceQrAnalise(true, chave, uri.toString(), extrairValor(query),
                    extrair(query, "estabelecimento", "emitente", "razao_social", "nome"),
                    normalizarCnpj(cnpj), null);
        } catch (IllegalArgumentException ignored) {
            return NfceQrAnalise.naoReconhecida();
        }
    }

    private BigDecimal extrairValor(String query) {
        try {
            String valor = extrair(query, "valor", "vNF", "total");
            return valor == null ? null : new BigDecimal(valor.replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extrair(String query, String... nomes) {
        for (String parte : query.split("&")) {
            String[] parametro = parte.split("=", 2);
            if (parametro.length != 2) continue;
            String nome = URLDecoder.decode(parametro[0], StandardCharsets.UTF_8);
            for (String esperado : nomes) {
                if (esperado.equalsIgnoreCase(nome)) {
                    return URLDecoder.decode(parametro[1], StandardCharsets.UTF_8).trim();
                }
            }
        }
        return null;
    }

    private String normalizarCnpj(String cnpj) {
        if (cnpj == null) return null;
        String somenteDigitos = cnpj.replaceAll("\\D", "");
        return somenteDigitos.length() == 14 ? somenteDigitos : null;
    }
}
