package com.mercadeira.api.compra.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.compra.repository.SolicitacaoPresencaCompraRepository;
import com.mercadeira.api.compra.repository.SolicitacaoResponsabilidadeOperacionalRepository;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoPresenca;
import com.mercadeira.api.compra.domain.EstadoSolicitacaoResponsabilidade;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarCompraDaLista {

    private final ListaCompraRepository listaRepository;
    private final MembroFamiliaRepository membroRepository;
    private final CompraRepository compraRepository;
    private final ParticipanteCompraRepository participanteRepository;
    private final ItemCompraRepository itemRepository;
    private final SolicitacaoPresencaCompraRepository solicitacaoRepository;
    private final SolicitacaoResponsabilidadeOperacionalRepository solicitacaoResponsabilidadeRepository;

    public ConsultarCompraDaLista(ListaCompraRepository listaRepository, MembroFamiliaRepository membroRepository,
            CompraRepository compraRepository, ParticipanteCompraRepository participanteRepository,
            ItemCompraRepository itemRepository, SolicitacaoPresencaCompraRepository solicitacaoRepository,
            SolicitacaoResponsabilidadeOperacionalRepository solicitacaoResponsabilidadeRepository) {
        this.listaRepository = listaRepository;
        this.membroRepository = membroRepository;
        this.compraRepository = compraRepository;
        this.participanteRepository = participanteRepository;
        this.itemRepository = itemRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.solicitacaoResponsabilidadeRepository = solicitacaoResponsabilidadeRepository;
    }

    @Transactional(readOnly = true)
    public ResultadoConsultaCompra consultar(UUID usuarioId, UUID familiaId, UUID listaId) {
        ListaCompra lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ListaCompraNaoEncontradaException(listaId));
        if (!lista.getFamilia().getId().equals(familiaId)) {
            throw new ListaCompraNaoEncontradaException(listaId);
        }
        MembroFamilia membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(
                familiaId, usuarioId, StatusMembroFamilia.ATIVO)
                .orElseThrow(MembroFamiliaInvalidoException::new);
        Compra compra = compraRepository.findByListaCompra_Id(listaId)
                .orElseThrow(() -> new CompraNaoEncontradaException(listaId));
        List<ParticipanteCompra> participantes = participanteRepository.findByCompra_IdOrderByGeradoEmAscIdAsc(compra.getId());
        List<ItemCompra> itens = itemRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId());
        var participanteAtual = participantes.stream().filter(p -> p.getMembroFamilia().getId().equals(membro.getId())).findFirst();
        boolean participanteCompra = participanteAtual.isPresent();
        var minhaSolicitacao = participanteAtual.flatMap(p -> solicitacaoRepository
                .findFirstByCompraIdAndSolicitanteIdOrderBySolicitadaEmDescIdDesc(compra.getId(), p.getId())).orElse(null);
        var pendentes = solicitacaoRepository.findByCompraIdAndEstadoOrderBySolicitadaEmAscIdAsc(compra.getId(), EstadoSolicitacaoPresenca.PENDENTE);
        var minhaResponsabilidade = participanteAtual.flatMap(p -> solicitacaoResponsabilidadeRepository
                .findFirstByCompraIdAndSolicitanteIdOrderBySolicitadaEmDescIdDesc(compra.getId(), p.getId())).orElse(null);
        var pendentesResponsabilidade = solicitacaoResponsabilidadeRepository.findByCompraIdAndEstadoOrderBySolicitadaEmAscIdAsc(compra.getId(), EstadoSolicitacaoResponsabilidade.PENDENTE);
        return new ResultadoConsultaCompra(compra, participantes, itens, participanteCompra,
                membro.getPapel() == PapelMembroFamilia.ADMINISTRADOR, minhaSolicitacao, pendentes,
                minhaResponsabilidade, pendentesResponsabilidade);
    }
}
