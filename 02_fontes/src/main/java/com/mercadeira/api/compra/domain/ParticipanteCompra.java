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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participante_lista_origem_id", nullable = false, unique = true)
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

    protected ParticipanteCompra() {
    }
}
