package com.mercadeira.api.compra.leiturapreco;

import java.math.BigDecimal;
import java.util.List;

/**
 * Identifica candidatos a preco em uma imagem. A escolha do candidato e a
 * persistencia pertencem a camadas superiores.
 */
public interface LeitorPrecoIa {
    List<BigDecimal> identificarPrecos(byte[] imagem, String contentType);
}
