package com.mercadeira.api.compra.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ListaCompra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_compra_id", nullable = false, unique = true)
    private ListaCompra listaCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iniciada_por_membro_familia_id", nullable = false)
    private MembroFamilia iniciadaPorMembroFamilia;

    @Column(name = "nome_lista_snapshot", nullable = false, length = 120)
    private String nomeListaSnapshot;

    @Column(name = "categoria_snapshot", nullable = false, length = 100)
    private String categoriaSnapshot;

    @Column(name = "estabelecimento_snapshot", length = 120)
    private String estabelecimentoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusCompra status;

    @Column(name = "iniciada_em", nullable = false)
    private Instant iniciadaEm;

    @Column(name = "finalizada_em")
    private Instant finalizadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalizada_por_participante_compra_id")
    private ParticipanteCompra finalizadaPorParticipanteCompra;

    @Column(name = "reaberta_em")
    private Instant reabertaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reaberta_por_membro_familia_id")
    private MembroFamilia reabertaPorMembroFamilia;

    @Column(name = "responsavel_operacional_id")
    private UUID responsavelOperacionalId;
    @Column(name = "ciclo_operacional", nullable = false)
    private long cicloOperacional;
    @Column(name = "ciclo_operacional_ativo", nullable = false)
    private boolean cicloOperacionalAtivo;
    @Column(name = "responsabilidade_revisao", nullable = false)
    private long responsabilidadeRevisao;
    @Column(name = "responsabilidade_anterior_id")
    private UUID responsabilidadeAnteriorId;
    @Column(name = "responsabilidade_alterada_por_id")
    private UUID responsabilidadeAlteradaPorId;
    @Column(name = "responsabilidade_alterada_em")
    private Instant responsabilidadeAlteradaEm;
    @Enumerated(EnumType.STRING)
    @Column(name = "responsabilidade_motivo", length = 30)
    private MotivoResponsabilidade responsabilidadeMotivo;

    public void mudarResponsabilidade(ParticipanteCompra destino, ParticipanteCompra autor,
            MotivoResponsabilidade motivo, Instant instante) {
        if (status != StatusCompra.EM_ANDAMENTO || motivo == null || instante == null)
            throw new IllegalArgumentException("Mudanca de responsabilidade invalida.");
        for (var participante : new ParticipanteCompra[]{destino, autor}) {
            if (participante != null && (id == null || !id.equals(participante.getCompra().getId())))
                throw new IllegalArgumentException("Responsabilidade deve referenciar a mesma compra.");
        }
        if (destino != null && (!destino.estaPresente()
                || destino.getMembroFamilia().getStatus() != com.mercadeira.api.familia.domain.StatusMembroFamilia.ATIVO))
            throw new IllegalArgumentException("Responsavel deve estar presente e elegivel.");
        if (destino != null && !cicloOperacionalAtivo) {
            cicloOperacional++;
            cicloOperacionalAtivo = true;
        }
        if (motivo == MotivoResponsabilidade.SEM_PRESENTES) cicloOperacionalAtivo = false;
        responsabilidadeAnteriorId = responsavelOperacionalId;
        responsavelOperacionalId = destino == null ? null : destino.getId();
        responsabilidadeAlteradaPorId = autor == null ? null : autor.getId();
        responsabilidadeAlteradaEm = instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        responsabilidadeMotivo = motivo;
        responsabilidadeRevisao++;
    }

    public UUID getResponsavelOperacionalId() { return responsavelOperacionalId; }
    public long getCicloOperacional() { return cicloOperacional; }
    public boolean isCicloOperacionalAtivo() { return cicloOperacionalAtivo; }
    public long getResponsabilidadeRevisao() { return responsabilidadeRevisao; }
    public UUID getResponsabilidadeAnteriorId() { return responsabilidadeAnteriorId; }
    public UUID getResponsabilidadeAlteradaPorId() { return responsabilidadeAlteradaPorId; }
    public Instant getResponsabilidadeAlteradaEm() { return responsabilidadeAlteradaEm; }
    public MotivoResponsabilidade getResponsabilidadeMotivo() { return responsabilidadeMotivo; }

    protected Compra() {
    }

    public static Compra iniciar(ListaCompra lista, MembroFamilia iniciador, Instant iniciadaEm) {
        Compra compra = new Compra();
        compra.listaCompra = lista;
        compra.iniciadaPorMembroFamilia = iniciador;
        compra.nomeListaSnapshot = lista.getNome();
        compra.categoriaSnapshot = lista.getCategoria().name();
        compra.estabelecimentoSnapshot = lista.getEstabelecimento();
        compra.status = StatusCompra.EM_ANDAMENTO;
        compra.iniciadaEm = iniciadaEm;
        return compra;
    }

    public boolean finalizar(ParticipanteCompra executor, Instant instante) {
        if (executor == null || instante == null || id == null
                || !id.equals(executor.getCompra().getId())) {
            throw new FinalizacaoCompraInvalidaException("Autor da finalizacao deve pertencer a mesma compra.");
        }
        if (status == StatusCompra.FINALIZADA) {
            if (finalizadaEm == null || finalizadaPorParticipanteCompra == null
                    || !id.equals(finalizadaPorParticipanteCompra.getCompra().getId())) {
                throw new FinalizacaoCompraInvalidaException("A compra finalizada possui autoria ou data inconsistente.");
            }
            return false;
        }
        if (status != StatusCompra.EM_ANDAMENTO) {
            throw new FinalizacaoCompraInvalidaException("A compra nao esta em andamento.");
        }
        if (finalizadaEm != null || finalizadaPorParticipanteCompra != null) {
            throw new FinalizacaoCompraInvalidaException("A compra em andamento possui finalizacao anterior.");
        }
        status = StatusCompra.FINALIZADA;
        cicloOperacionalAtivo = false;
        finalizadaPorParticipanteCompra = executor;
        finalizadaEm = instante;
        return true;
    }

    public StatusCompra getStatus() {
        return status;
    }

    public UUID getId() { return id; }
    public ListaCompra getListaCompra() { return listaCompra; }
    public String getNomeListaSnapshot() { return nomeListaSnapshot; }
    public String getCategoriaSnapshot() { return categoriaSnapshot; }
    public String getEstabelecimentoSnapshot() { return estabelecimentoSnapshot; }
    public Instant getIniciadaEm() { return iniciadaEm; }
    public Instant getFinalizadaEm() { return finalizadaEm; }
    public ParticipanteCompra getFinalizadaPorParticipanteCompra() { return finalizadaPorParticipanteCompra; }
}
