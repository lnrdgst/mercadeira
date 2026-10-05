package com.mercadeira.api.compra.application;

import java.util.List;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import com.mercadeira.api.compra.domain.SolicitacaoPresencaCompra;
import com.mercadeira.api.compra.domain.SolicitacaoResponsabilidadeOperacional;

public record ResultadoConsultaCompra(
        Compra compra,
        List<ParticipanteCompra> participantes,
        List<ItemCompra> itens,
        List<RegistroFinanceiroCompra> registrosFinanceiros,
        EstadoAlertaContinuidadeCompra alertaContinuidade,
        boolean participanteCompra,
        boolean administradorAtivo,
        SolicitacaoPresencaCompra minhaSolicitacao,
        List<SolicitacaoPresencaCompra> solicitacoesPendentes,
        SolicitacaoResponsabilidadeOperacional minhaSolicitacaoResponsabilidade,
        List<SolicitacaoResponsabilidadeOperacional> solicitacoesResponsabilidadePendentes) {
}
