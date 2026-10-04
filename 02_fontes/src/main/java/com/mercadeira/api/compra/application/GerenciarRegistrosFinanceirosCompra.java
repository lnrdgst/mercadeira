package com.mercadeira.api.compra.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.compra.domain.PresencaOperacional;
import com.mercadeira.api.compra.domain.RegistroFinanceiroCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.compra.repository.RegistroFinanceiroCompraRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GerenciarRegistrosFinanceirosCompra {

    private final ListaCompraRepository listaRepository;
    private final CompraRepository compraRepository;
    private final MembroFamiliaRepository membroRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final RegistroFinanceiroCompraRepository registroRepository;
    private final Clock clock;

    public GerenciarRegistrosFinanceirosCompra(ListaCompraRepository listaRepository, CompraRepository compraRepository,
            MembroFamiliaRepository membroRepository, ParticipanteCompraRepository participanteRepository,
            RegistroFinanceiroCompraRepository registroRepository, Clock clock) {
        this.listaRepository = listaRepository;
        this.compraRepository = compraRepository;
        this.membroRepository = membroRepository;
        this.participanteRepository = participanteRepository;
        this.registroRepository = registroRepository;
        this.clock = clock;
    }

    @Transactional
    public RegistroFinanceiroCompra adicionar(UUID usuarioId, UUID familiaId, UUID listaId,
            AdicionarRegistroFinanceiroCompraCommand command) {
        if (command.valor() == null || command.valor().signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero.");
        }
        var compra = carregarCompraOperacional(usuarioId, familiaId, listaId);
        return registroRepository.save(RegistroFinanceiroCompra.manual(
                compra, command.valor(), command.estabelecimentoNome(), clock.instant()));
    }

    @Transactional
    public void remover(UUID usuarioId, UUID familiaId, UUID listaId, UUID registroId) {
        var compra = carregarCompraOperacional(usuarioId, familiaId, listaId);
        var registro = registroRepository.findByIdAndCompra_Id(registroId, compra.getId())
                .orElseThrow(RegistroFinanceiroCompraNaoEncontradoException::new);
        registroRepository.delete(registro);
    }
    private com.mercadeira.api.compra.domain.Compra carregarCompraOperacional(
            UUID usuarioId, UUID familiaId, UUID listaId) {
        var lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        var membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        var compra = compraRepository.findByListaCompra_IdForUpdate(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO && compra.getStatus() != StatusCompra.FINALIZADA) {
            throw new CompraForaDeAndamentoException();
        }
        var participante = participanteRepository.findByCompra_IdAndMembroFamilia_Id(compra.getId(), membro.getId())
                .orElseThrow(UsuarioNaoParticipaDaCompraException::new);
        if (compra.getStatus() == StatusCompra.EM_ANDAMENTO && participante.getPresencaOperacional() == PresencaOperacional.NAO_INFORMADA) {
            throw new PresencaOperacionalObrigatoriaException();
        }
        return compra;
    }
}
