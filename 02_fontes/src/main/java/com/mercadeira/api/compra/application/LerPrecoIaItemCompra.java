package com.mercadeira.api.compra.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.leiturapreco.LeitorPrecoIa;
import org.springframework.stereotype.Service;

@Service
public class LerPrecoIaItemCompra {
    private final RegistrarDadosItemCompra registrarDadosItemCompra;
    private final LeitorPrecoIa leitorPrecoIa;

    public LerPrecoIaItemCompra(RegistrarDadosItemCompra registrarDadosItemCompra, LeitorPrecoIa leitorPrecoIa) {
        this.registrarDadosItemCompra = registrarDadosItemCompra;
        this.leitorPrecoIa = leitorPrecoIa;
    }

    public List<BigDecimal> executar(UUID usuarioId, UUID familiaId, UUID listaId, UUID itemCompraId,
            byte[] imagem, String contentType) {
        registrarDadosItemCompra.validarEdicao(usuarioId, familiaId, listaId, itemCompraId);
        return leitorPrecoIa.identificarPrecos(imagem, contentType);
    }
}
