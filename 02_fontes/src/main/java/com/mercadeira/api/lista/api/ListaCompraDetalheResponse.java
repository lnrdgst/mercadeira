package com.mercadeira.api.lista.api;

import java.time.Instant;
import java.util.UUID;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.lista.domain.*;

public record ListaCompraDetalheResponse(UUID id, String nome, CategoriaCompra categoria, String estabelecimento,
        StatusListaCompra status, Instant criadaEm, Instant atualizadaEm, Criador criador, ContextoUsuario contextoUsuario) {
    public record Criador(UUID membroFamiliaId, UUID usuarioId, String nome) { }
    public record ContextoUsuario(UUID membroFamiliaId, PapelMembroFamilia papelFamilia, boolean participanteAtivo,
            boolean podeGerenciarParticipantes, boolean podeAlterarItens) { }
    static ListaCompraDetalheResponse from(ListaCompra lista, MembroFamilia membro, boolean participanteAtivo) {
        MembroFamilia criador = lista.getCriadaPorMembroFamilia();
        boolean gerencia = criador.getId().equals(membro.getId()) || membro.getPapel() == PapelMembroFamilia.ADMINISTRADOR;
        return new ListaCompraDetalheResponse(lista.getId(), lista.getNome(), lista.getCategoria(), lista.getEstabelecimento(), lista.getStatus(), lista.getCriadaEm(), lista.getAtualizadaEm(),
                new Criador(criador.getId(), criador.getUsuario().getId(), criador.getUsuario().getNome()),
                new ContextoUsuario(membro.getId(), membro.getPapel(), participanteAtivo, gerencia, participanteAtivo && lista.getStatus() == StatusListaCompra.EM_PREPARACAO));
    }
}
