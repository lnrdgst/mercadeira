package com.mercadeira.api.familia.application;

import java.security.SecureRandom;

import com.mercadeira.api.familia.repository.FamiliaRepository;
import org.springframework.stereotype.Component;

@Component
public class GeradorCodigoIngresso {

    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TAMANHO_CODIGO = 8;
    private static final int MAXIMO_TENTATIVAS = 20;

    private final FamiliaRepository familiaRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public GeradorCodigoIngresso(FamiliaRepository familiaRepository) {
        this.familiaRepository = familiaRepository;
    }

    public String gerar() {
        for (int tentativa = 0; tentativa < MAXIMO_TENTATIVAS; tentativa++) {
            String codigo = proximoCodigo();
            if (!familiaRepository.existsByCodigoIngresso(codigo)) {
                return codigo;
            }
        }
        throw new CodigoIngressoIndisponivelException();
    }

    private String proximoCodigo() {
        StringBuilder codigo = new StringBuilder(TAMANHO_CODIGO);
        for (int indice = 0; indice < TAMANHO_CODIGO; indice++) {
            codigo.append(ALFABETO[secureRandom.nextInt(ALFABETO.length)]);
        }
        return codigo.toString();
    }
}
