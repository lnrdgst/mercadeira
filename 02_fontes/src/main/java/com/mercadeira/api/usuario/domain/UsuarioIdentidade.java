package com.mercadeira.api.usuario.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario_identidade")
public class UsuarioIdentidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false, length = 20)
    private ProvedorIdentidadeUsuario provedor;

    @Column(name = "provedor_subject", length = 255)
    private String provedorSubject;

    @Column(name = "email_provedor", length = 255)
    private String emailProvedor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected UsuarioIdentidade() {
    }

    public static UsuarioIdentidade local(Usuario usuario, Instant agora) {
        return criar(usuario, ProvedorIdentidadeUsuario.LOCAL, null, usuario.getEmail(), agora);
    }

    public static UsuarioIdentidade google(Usuario usuario, String subject, String email, Instant agora) {
        return criar(usuario, ProvedorIdentidadeUsuario.GOOGLE, subject, email, agora);
    }

    private static UsuarioIdentidade criar(Usuario usuario, ProvedorIdentidadeUsuario provedor, String subject,
            String email, Instant agora) {
        UsuarioIdentidade identidade = new UsuarioIdentidade();
        identidade.usuario = usuario;
        identidade.provedor = provedor;
        identidade.provedorSubject = subject;
        identidade.emailProvedor = email;
        identidade.criadoEm = agora;
        identidade.atualizadoEm = agora;
        return identidade;
    }

    public Usuario getUsuario() { return usuario; }
    public ProvedorIdentidadeUsuario getProvedor() { return provedor; }
    public String getProvedorSubject() { return provedorSubject; }
    public String getEmailProvedor() { return emailProvedor; }

    public void atualizarEmailProvedor(String email, Instant agora) {
        this.emailProvedor = email;
        this.atualizadoEm = agora;
    }
}
