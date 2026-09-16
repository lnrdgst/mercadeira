package com.mercadeira.api.compra.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.lista.domain.ParticipanteLista;
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
@Table(name = "participante_compra")
public class ParticipanteCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "participante_lista_origem_id")
    private ParticipanteLista participanteListaOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membro_familia_id", nullable = false)
    private MembroFamilia membroFamilia;

    @Column(name = "nome_snapshot", nullable = false, length = 120)
    private String nomeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel_snapshot", nullable = false, length = 30)
    private PapelMembroFamilia papelSnapshot;

    @Column(name = "gerado_em", nullable = false)
    private Instant geradoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "presenca_operacional", nullable = false, length = 20)
    private PresencaOperacional presencaOperacional = PresencaOperacional.NAO_INFORMADA;

    @Column(name = "presenca_alterada_em")
    private Instant presencaAlteradaEm;

    public boolean alterarPresenca(PresencaOperacional estado, Instant instante) {
        if (estado == null || estado == PresencaOperacional.NAO_INFORMADA || instante == null) {
            throw new IllegalArgumentException("Declare PRESENTE ou NAO_PRESENTE com um instante valido.");
        }
        if (compra.getStatus() != StatusCompra.EM_ANDAMENTO) {
            throw new com.mercadeira.api.compra.application.CompraForaDeAndamentoException();
        }
        if (presencaOperacional == estado) return false;
        presencaOperacional = estado;
        presencaAlteradaEm = instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return true;
    }

    public PresencaOperacional getPresencaOperacional() { return presencaOperacional; }
    public Instant getPresencaAlteradaEm() { return presencaAlteradaEm; }
    public boolean estaPresente() { return presencaOperacional == PresencaOperacional.PRESENTE; }

    protected ParticipanteCompra() {
    }

    public static ParticipanteCompra criarDaPreparacao(Compra compra, ParticipanteLista origem, Instant geradoEm) {
        return criar(compra, origem, origem.getMembroFamilia(), geradoEm);
    }

    public static ParticipanteCompra criarDireto(Compra compra, MembroFamilia membro, Instant geradoEm) {
        return criar(compra, null, membro, geradoEm);
    }

    private static ParticipanteCompra criar(Compra compra, ParticipanteLista origem, MembroFamilia membro, Instant geradoEm) {
        ParticipanteCompra participante = new ParticipanteCompra();
        participante.compra = compra;
        participante.participanteListaOrigem = origem;
        participante.membroFamilia = membro;
        participante.nomeSnapshot = membro.getUsuario().getNome();
        participante.papelSnapshot = membro.getPapel();
        participante.geradoEm = geradoEm;
        return participante;
    }

    public Compra getCompra() { return compra; }
    public UUID getId() { return id; }
    public MembroFamilia getMembroFamilia() { return membroFamilia; }
    public String getNomeSnapshot() { return nomeSnapshot; }
    public PapelMembroFamilia getPapelSnapshot() { return papelSnapshot; }
    public Instant getGeradoEm() { return geradoEm; }
}
