package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncerrarCompraProlongada {

    private final ListaCompraRepository listas;
    private final CompraRepository compras;
    private final MembroFamiliaRepository membros;
    private final ParticipanteCompraRepository participantes;
    private final AlertaContinuidadeCompra alerta;
    private final Clock clock;

    public EncerrarCompraProlongada(ListaCompraRepository listas, CompraRepository compras,
            MembroFamiliaRepository membros, ParticipanteCompraRepository participantes,
            AlertaContinuidadeCompra alerta, Clock clock) {
        this.listas = listas;
        this.compras = compras;
        this.membros = membros;
        this.participantes = participantes;
        this.alerta = alerta;
        this.clock = clock;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID familiaId, UUID listaId) {
        var lista = listas.findByIdForUpdate(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var compra = compras.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (!compra.getListaCompra().getId().equals(listaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var participante = participantes.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);

        if (compra.getStatus() == StatusCompra.CANCELADA && lista.getStatus() == StatusListaCompra.CANCELADA) {
            return;
        }
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO || lista.getStatus() != StatusListaCompra.EM_COMPRA) {
            throw new CompraListaInconsistenteException();
        }
        if (compra.getResponsavelOperacionalId() == null
                || !compra.getResponsavelOperacionalId().equals(participante.getId())) {
            throw new AutoridadePresencaException();
        }
        if (!alerta.avaliar(compra, participante.getId()).necessario()) {
            throw new CompraForaDeAndamentoException();
        }

        var instante = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        compra.cancelar(participante);
        lista.cancelarCompra(instante);
    }
}
