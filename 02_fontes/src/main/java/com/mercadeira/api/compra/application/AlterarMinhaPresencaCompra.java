package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;
import com.mercadeira.api.compra.domain.PresencaOperacional;
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
public class AlterarMinhaPresencaCompra {
    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final Clock clock;

    public AlterarMinhaPresencaCompra(ListaCompraRepository listas, CompraRepository compras,
            MembroFamiliaRepository membros, ParticipanteCompraRepository participantes, Clock clock) {
        this.listas = listas;
        this.compras = compras;
        this.membros = membros;
        this.participantes = participantes;
        this.clock = clock;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID familiaId, UUID listaId, PresencaOperacional estado) {
        if (estado == null || estado == PresencaOperacional.NAO_INFORMADA) {
            throw new IllegalArgumentException("Declare PRESENTE ou NAO_PRESENTE.");
        }
        var lista = listas.findById(listaId).orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) throw new ListaCompraNaoEncontradaException(listaId);
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        // Mesma barreira das mutacoes de itens e finalizacao. Ler participante somente apos o lock.
        var compra = compras.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        participante.alterarPresenca(estado, clock.instant());
    }
}
