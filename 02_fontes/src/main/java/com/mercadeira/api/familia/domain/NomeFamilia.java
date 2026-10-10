package com.mercadeira.api.familia.domain;

public final class NomeFamilia {

    private NomeFamilia() {
    }

    public static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da família é obrigatório.");
        }

        String nomeNormalizado = nome
                .trim()
                .replaceFirst("(?iu)^fam[ií]lia(?:\\s+|$)", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (nomeNormalizado.isBlank()) {
            throw new IllegalArgumentException("O nome da família é obrigatório.");
        }

        return nomeNormalizado;
    }
}
