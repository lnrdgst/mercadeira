package com.mercadeira.api.compra.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import com.mercadeira.api.compra.application.ConflitoPresencaException;

@Entity
@Table(name = "solicitacao_presenca_compra")
public class SolicitacaoPresencaCompra {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "compra_id", nullable = false, updatable = false)
    private UUID compraId;
    @Column(name = "solicitante_id", nullable = false, updatable = false)
    private UUID solicitanteId;
    @Column(name = "ciclo_operacional", nullable = false, updatable = false)
    private long cicloOperacional;
    @Column(name = "solicitada_em", nullable = false, updatable = false)
    private Instant solicitadaEm;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoSolicitacaoPresenca estado;
    @Column(name = "encerrada_por_id")
    private UUID encerradaPorId;
    @Column(name = "encerrada_em")
    private Instant encerradaEm;
    @Enumerated(EnumType.STRING) @Column(name = "motivo_cancelamento", length = 30)
    private MotivoCancelamentoPresenca motivoCancelamento;

    protected SolicitacaoPresencaCompra() {}

    public static SolicitacaoPresencaCompra criar(Compra compra, ParticipanteCompra solicitante, Instant agora) {
        if (!compra.isCicloOperacionalAtivo() || compra.getStatus() != StatusCompra.EM_ANDAMENTO
                || !compra.getId().equals(solicitante.getCompra().getId()) || solicitante.estaPresente())
            throw new IllegalArgumentException("Solicitacao de presenca invalida.");
        var pedido = new SolicitacaoPresencaCompra();
        pedido.compraId = compra.getId();
        pedido.solicitanteId = solicitante.getId();
        pedido.cicloOperacional = compra.getCicloOperacional();
        pedido.solicitadaEm = agora.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        pedido.estado = EstadoSolicitacaoPresenca.PENDENTE;
        return pedido;
    }

    public boolean encerrar(EstadoSolicitacaoPresenca destino, UUID executor,
            MotivoCancelamentoPresenca motivo, Instant agora) {
        if (destino == null || destino == EstadoSolicitacaoPresenca.PENDENTE || agora == null
                || (destino != EstadoSolicitacaoPresenca.CANCELADA && (executor == null || motivo != null))
                || (destino == EstadoSolicitacaoPresenca.CANCELADA && (motivo == null
                    || (motivo == MotivoCancelamentoPresenca.SOLICITANTE ? !solicitanteId.equals(executor) : executor != null))))
            throw new IllegalArgumentException("Encerramento de solicitacao invalido.");
        if (estado != EstadoSolicitacaoPresenca.PENDENTE) {
            if (estado == destino && java.util.Objects.equals(encerradaPorId, executor)
                    && motivoCancelamento == motivo) return false;
            throw new ConflitoPresencaException("A solicitacao ja foi encerrada. Atualize a compra.");
        }
        estado = destino;
        encerradaPorId = executor;
        encerradaEm = agora.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        motivoCancelamento = motivo;
        return true;
    }
    public UUID getId() { return id; }
    public UUID getCompraId() { return compraId; }
    public UUID getSolicitanteId() { return solicitanteId; }
    public long getCicloOperacional() { return cicloOperacional; }
    public Instant getSolicitadaEm() { return solicitadaEm; }
    public EstadoSolicitacaoPresenca getEstado() { return estado; }
    public UUID getEncerradaPorId() { return encerradaPorId; }
    public Instant getEncerradaEm() { return encerradaEm; }
    public MotivoCancelamentoPresenca getMotivoCancelamento() { return motivoCancelamento; }
}
