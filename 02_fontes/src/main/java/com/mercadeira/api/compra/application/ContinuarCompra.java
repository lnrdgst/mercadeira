package com.mercadeira.api.compra.application;

import java.util.UUID;

import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContinuarCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final AlertaContinuidadeCompra alerta;

    public ContinuarCompra(ListaCompraRepository listas, CompraRepository compras, MembroFamiliaRepository membros,
            ParticipanteCompraRepository participantes, AlertaContinuidadeCompra alerta) {
        this.listas = listas;
        this.compras = compras;
        this.membros = membros;
        this.participantes = participantes;
        this.alerta = alerta;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID familiaId, UUID listaId) {
        var lista = listas.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var compra = compras.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        compra.adiarAlertaContinuidade(participante, alerta.proximoAlerta());
    }
}
