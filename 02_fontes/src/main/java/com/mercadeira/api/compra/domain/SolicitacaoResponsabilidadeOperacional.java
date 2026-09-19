package com.mercadeira.api.compra.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import com.mercadeira.api.compra.application.ConflitoPresencaException;

@Entity
@Table(name = "solicitacao_responsabilidade_operacional")
public class SolicitacaoResponsabilidadeOperacional {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "compra_id", nullable = false, updatable = false) private UUID compraId;
    @Column(name = "solicitante_id", nullable = false, updatable = false) private UUID solicitanteId;
    @Column(name = "responsavel_atual_id", nullable = false, updatable = false) private UUID responsavelAtualId;
    @Column(name = "ciclo_operacional", nullable = false, updatable = false) private long cicloOperacional;
    @Column(name = "responsabilidade_revisao", nullable = false, updatable = false) private long responsabilidadeRevisao;
    @Column(name = "solicitada_em", nullable = false, updatable = false) private Instant solicitadaEm;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoSolicitacaoResponsabilidade estado;
    @Column(name = "encerrada_por_id") private UUID encerradaPorId;
    @Column(name = "encerrada_em") private Instant encerradaEm;
    protected SolicitacaoResponsabilidadeOperacional() {}
    public static SolicitacaoResponsabilidadeOperacional criar(Compra compra, ParticipanteCompra solicitante, ParticipanteCompra responsavel, Instant agora) {
        var pedido = new SolicitacaoResponsabilidadeOperacional();
        pedido.compraId=compra.getId(); pedido.solicitanteId=solicitante.getId(); pedido.responsavelAtualId=responsavel.getId();
        pedido.cicloOperacional=compra.getCicloOperacional(); pedido.responsabilidadeRevisao=compra.getResponsabilidadeRevisao();
        pedido.solicitadaEm=agora; pedido.estado=EstadoSolicitacaoResponsabilidade.PENDENTE; return pedido;
    }
    public boolean encerrar(EstadoSolicitacaoResponsabilidade destino, UUID executor, Instant agora) {
        if (destino == null || destino == EstadoSolicitacaoResponsabilidade.PENDENTE || agora == null) throw new IllegalArgumentException("Encerramento invalido.");
        if (estado != EstadoSolicitacaoResponsabilidade.PENDENTE) {
            if (estado == destino && java.util.Objects.equals(encerradaPorId, executor)) return false;
            throw new ConflitoPresencaException("A solicitacao de responsabilidade ja foi encerrada. Atualize a compra.");
        }
        estado=destino; encerradaPorId=executor; encerradaEm=agora; return true;
    }
    public UUID getId(){return id;} public UUID getCompraId(){return compraId;} public UUID getSolicitanteId(){return solicitanteId;}
    public UUID getResponsavelAtualId(){return responsavelAtualId;} public long getCicloOperacional(){return cicloOperacional;}
    public long getResponsabilidadeRevisao(){return responsabilidadeRevisao;} public Instant getSolicitadaEm(){return solicitadaEm;}
    public EstadoSolicitacaoResponsabilidade getEstado(){return estado;} public UUID getEncerradaPorId(){return encerradaPorId;} public Instant getEncerradaEm(){return encerradaEm;}
}
