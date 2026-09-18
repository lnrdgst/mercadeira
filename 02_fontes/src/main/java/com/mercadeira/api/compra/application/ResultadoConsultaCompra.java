package com.mercadeira.api.compra.application;

import java.util.List;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.SolicitacaoPresencaCompra;

public record ResultadoConsultaCompra(
        Compra compra,
        List<ParticipanteCompra> participantes,
        List<ItemCompra> itens,
        boolean participanteCompra,
        SolicitacaoPresencaCompra minhaSolicitacao,
        List<SolicitacaoPresencaCompra> solicitacoesPendentes) {
}
