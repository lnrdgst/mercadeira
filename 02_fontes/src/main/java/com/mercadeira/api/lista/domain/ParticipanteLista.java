package com.mercadeira.api.lista.domain;

import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "participante_lista")
public class ParticipanteLista {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_compra_id", nullable = false)
    private ListaCompra listaCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membro_familia_id", nullable = false)
    private MembroFamilia membroFamilia;

    @Column(name = "entrou_em", nullable = false)
    private Instant entrouEm;

    @Column(name = "saiu_em")
    private Instant saiuEm;

    protected ParticipanteLista() {
    }

    public static ParticipanteLista criar(ListaCompra listaCompra, MembroFamilia membroFamilia, Instant agora) {
        ParticipanteLista participante = new ParticipanteLista();
        participante.listaCompra = listaCompra;
        participante.membroFamilia = membroFamilia;
        participante.entrouEm = agora;
        return participante;
    }

    public void reativar(Instant agora) {
        saiuEm = null;
        entrouEm = agora;
    }

    public void sair(Instant agora) {
        saiuEm = agora;
    }

    public UUID getId() { return id; }
    public ListaCompra getListaCompra() { return listaCompra; }
    public MembroFamilia getMembroFamilia() { return membroFamilia; }
    public Instant getEntrouEm() { return entrouEm; }
    public Instant getSaiuEm() { return saiuEm; }
}
