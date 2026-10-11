package com.mercadeira.api.compra.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.lista.domain.UnidadeMedida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "solicitacao_item_compra")
public class SolicitacaoItemCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "compra_id", nullable = false, updatable = false)
    private UUID compraId;

    @Column(name = "solicitante_id", nullable = false, updatable = false)
    private UUID solicitanteId;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", length = 30)
    private UnidadeMedida unidadeMedida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitacaoItemCompra status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "decidido_por_id")
    private UUID decididoPorId;

    protected SolicitacaoItemCompra() {}

    public static SolicitacaoItemCompra criar(Compra compra, ParticipanteCompra solicitante, String descricao,
            BigDecimal quantidade, UnidadeMedida unidadeMedida, Instant agora) {
        if (compra == null || compra.getId() == null || solicitante == null || solicitante.getId() == null
                || !compra.getId().equals(solicitante.getCompra().getId())
                || descricao == null || descricao.isBlank() || descricao.length() > 200
                || (quantidade != null && quantidade.signum() <= 0) || agora == null)
            throw new IllegalArgumentException("Solicitacao de item invalida.");
        var solicitacao = new SolicitacaoItemCompra();
        solicitacao.compraId = compra.getId();
        solicitacao.solicitanteId = solicitante.getId();
        solicitacao.descricao = descricao;
        solicitacao.quantidade = quantidade;
        solicitacao.unidadeMedida = unidadeMedida;
        solicitacao.status = EstadoSolicitacaoItemCompra.PENDENTE;
        solicitacao.criadoEm = agora;
        return solicitacao;
    }
    public UUID getId() { return id; }
    public UUID getCompraId() { return compraId; }
    public UUID getSolicitanteId() { return solicitanteId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getQuantidade() { return quantidade; }
    public UnidadeMedida getUnidadeMedida() { return unidadeMedida; }
    public EstadoSolicitacaoItemCompra getStatus() { return status; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getDecididoEm() { return decididoEm; }
    public UUID getDecididoPorId() { return decididoPorId; }

    public void aprovar(ParticipanteCompra decisor, Instant instante) {
        decidir(EstadoSolicitacaoItemCompra.APROVADA, decisor, instante);
    }

    public void recusar(ParticipanteCompra decisor, Instant instante) {
        decidir(EstadoSolicitacaoItemCompra.RECUSADA, decisor, instante);
    }

    private void decidir(EstadoSolicitacaoItemCompra destino, ParticipanteCompra decisor, Instant instante) {
        if (status != EstadoSolicitacaoItemCompra.PENDENTE) throw new SolicitacaoItemCompraNaoPendenteException();
        if (decisor == null || decisor.getId() == null || !compraId.equals(decisor.getCompra().getId())
                || instante == null || instante.isBefore(criadoEm))
            throw new IllegalArgumentException("Decisao de solicitacao de item invalida.");
        status = destino;
        decididoEm = instante;
        decididoPorId = decisor.getId();
    }
}
