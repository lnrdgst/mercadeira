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

    @Column(name = "reaberta_em")
    private Instant reabertaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reaberta_por_membro_familia_id")
    private MembroFamilia reabertaPorMembroFamilia;

    protected Compra() {
    }
}
