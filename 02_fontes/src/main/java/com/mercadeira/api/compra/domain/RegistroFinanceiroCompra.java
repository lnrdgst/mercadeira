package com.mercadeira.api.compra.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "registro_financeiro_compra")
public class RegistroFinanceiroCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoRegistroFinanceiroCompra tipo;

    @Column(name = "estabelecimento_nome", length = 120)
    private String estabelecimentoNome;

    @Column(name = "chave_nfce", length = 44)
    private String chaveNfce;
    @Column(name = "url_consulta", length = 1000)
    private String urlConsulta;
    @Column(name = "cnpj_emitente", length = 14)
    private String cnpjEmitente;
    @Column(name = "data_hora_documento")
    private Instant dataHoraDocumento;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RegistroFinanceiroCompra() {
    }

    public static RegistroFinanceiroCompra manual(Compra compra, BigDecimal valor, String estabelecimentoNome, Instant criadoEm) {
        if (compra == null || valor == null || valor.signum() <= 0 || criadoEm == null) {
            throw new IllegalArgumentException("Registro financeiro invalido.");
        }
        RegistroFinanceiroCompra registro = new RegistroFinanceiroCompra();
        registro.compra = compra;
        registro.valor = valor;
        registro.tipo = TipoRegistroFinanceiroCompra.MANUAL;
        registro.estabelecimentoNome = normalizar(estabelecimentoNome);
        registro.criadoEm = criadoEm;
        return registro;
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    public UUID getId() { return id; }
    public Compra getCompra() { return compra; }
    public BigDecimal getValor() { return valor; }
    public TipoRegistroFinanceiroCompra getTipo() { return tipo; }
    public String getEstabelecimentoNome() { return estabelecimentoNome; }
    public Instant getCriadoEm() { return criadoEm; }
    public String getChaveNfce() { return chaveNfce; }
    public String getUrlConsulta() { return urlConsulta; }
    public String getCnpjEmitente() { return cnpjEmitente; }
    public Instant getDataHoraDocumento() { return dataHoraDocumento; }

}
