package com.mercadeira.api.compra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
class CompraV8IntegrationTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired UsuarioRepository usuarios;
    @Autowired FamiliaRepository familias;
    @Autowired MembroFamiliaRepository membros;
    @Autowired ListaCompraRepository listas;
    @Autowired CompraRepository compras;
    @Autowired ParticipanteCompraRepository participantes;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;

    private static final Instant AGORA = Instant.parse("2026-09-10T18:00:00Z");

    @Test
    void flywayAplicaV1AteV9EHibernateValidaSchema() {
        assertThat(jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @ParameterizedTest
    @CsvSource({"EM_ANDAMENTO,false", "CANCELADA,false", "EM_ANDAMENTO,true", "CANCELADA,true", "FINALIZADA,true"})
    void permiteEstadosComPreenchimentoCoerente(String status, boolean preenchida) {
        var c = contexto();
        atualizar(c, status, preenchida ? c.participante().getId() : null, preenchida ? AGORA : null);
        em.clear();
        var compra = compras.findById(c.compra().getId()).orElseThrow();
        assertThat(compra.getStatus()).isEqualTo(StatusCompra.valueOf(status));
        assertThat(compra.getFinalizadaEm()).isEqualTo(preenchida ? AGORA : null);
        if (preenchida) {
            assertThat(compra.getFinalizadaPorParticipanteCompra().getId()).isEqualTo(c.participante().getId());
        } else {
            assertThat(compra.getFinalizadaPorParticipanteCompra()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "FINALIZADA,false,false", "FINALIZADA,false,true", "FINALIZADA,true,false",
        "EM_ANDAMENTO,true,false", "EM_ANDAMENTO,false,true",
        "CANCELADA,true,false", "CANCELADA,false,true"
    })
    void bancoRejeitaFinalizacaoIncompleta(String status, boolean autor, boolean timestamp) {
        var c = contexto();
        assertThatThrownBy(() -> atualizar(c, status, autor ? c.participante().getId() : null, timestamp ? AGORA : null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_compra_finalizacao_preenchida");
    }

    @Test
    void fkCompostaRejeitaParticipanteDeOutraCompra() {
        var c = contexto();
        var outra = contexto();
        assertThatThrownBy(() -> atualizar(c, "FINALIZADA", outra.participante().getId(), AGORA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_compra_finalizada_por_mesma_compra");
    }

    @Test
    void associacaoJpaPermaneceLazyEPreservaNomeSnapshot() {
        var c = contexto();
        atualizar(c, "FINALIZADA", c.participante().getId(), AGORA);
        jdbc.update("update usuario set nome = 'Nome atual alterado' where id = ?",
                c.participante().getMembroFamilia().getUsuario().getId());
        em.clear();
        var compra = compras.findById(c.compra().getId()).orElseThrow();
        var autor = compra.getFinalizadaPorParticipanteCompra();
        assertThat(Hibernate.isInitialized(autor)).isFalse();
        assertThat(autor.getId()).isEqualTo(c.participante().getId());
        assertThat(Hibernate.isInitialized(autor)).isFalse();
        assertThat(autor.getNomeSnapshot()).isEqualTo("Ana");
        assertThat(Hibernate.isInitialized(autor)).isTrue();
    }

    @Test
    void postgresExigeUniqueDoConjuntoReferenciadoMesmoComIdPrimaryKey() throws Exception {
        // Conexao independente: o erro esperado nao aborta a transacao do fixture JPA.
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("create temporary table participante_fk (id uuid primary key, compra_id uuid not null)");
            statement.execute("create temporary table compra_fk (id uuid primary key, autor uuid)");
            assertThatThrownBy(() -> statement.execute(
                    "alter table compra_fk add foreign key (id, autor) references participante_fk (compra_id, id)"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("42830");
            statement.execute("alter table participante_fk add unique (compra_id, id)");
            statement.execute("alter table compra_fk add foreign key (id, autor) references participante_fk (compra_id, id)");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "EM_ANDAMENTO,false,true", "CANCELADA,false,true",
        "FINALIZADA,false,false", "FINALIZADA,true,false", "EM_ANDAMENTO,true,false"
    })
    void migracaoDeV7PreservaDadosOuInterrompeSemInventarAutor(String status, boolean timestamp, boolean compativel)
            throws Exception {
        String schema = "legado_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "7").migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            connection.setSchema(schema);
            // UUIDs fixos apenas neste schema isolado, com todas as FKs de V7 satisfeitas.
            statement.execute("""
                    insert into usuario (id,nome,email,criado_em,atualizado_em,senha_hash)
                    values ('00000000-0000-0000-0000-000000000001','Legado','legado@test.local',now(),now(),'hash')
                    """);
            statement.execute("""
                    insert into familia (id,nome,criada_por_usuario_id,criada_em,atualizada_em,status,codigo_ingresso)
                    values ('00000000-0000-0000-0000-000000000002','Familia','00000000-0000-0000-0000-000000000001',now(),now(),'ATIVA','LEGADO')
                    """);
            statement.execute("""
                    insert into membro_familia (id,familia_id,usuario_id,papel,status,criado_em,atualizado_em)
                    values ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002',
                    '00000000-0000-0000-0000-000000000001','ADMINISTRADOR','ATIVO',now(),now())
                    """);
            statement.execute("""
                    insert into lista_compra (id,familia_id,nome,categoria,criada_por_membro_familia_id,criada_em,atualizada_em,status)
                    values ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002',
                    'Lista','SUPERMERCADO','00000000-0000-0000-0000-000000000003',now(),now(),'EM_COMPRA')
                    """);
            try (var insert = connection.prepareStatement("""
                    insert into compra (id,lista_compra_id,iniciada_por_membro_familia_id,nome_lista_snapshot,
                    categoria_snapshot,status,iniciada_em,finalizada_em)
                    values ('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000004',
                    '00000000-0000-0000-0000-000000000003','Lista','SUPERMERCADO',?,now(),?)
                    """)) {
                insert.setString(1, status);
                insert.setTimestamp(2, timestamp ? Timestamp.from(AGORA) : null);
                insert.executeUpdate();
            }
            if (compativel) {
                assertThat(flyway(schema, "8").migrate().migrationsExecuted).isEqualTo(1);
                try (var result = statement.executeQuery("select status, finalizada_em, finalizada_por_participante_compra_id from compra")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(status);
                    assertThat(result.getTimestamp(2)).isNull();
                    assertThat(result.getObject(3)).isNull();
                }
            } else {
                assertThatThrownBy(() -> flyway(schema, "8").migrate())
                        .isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("V8: existem compras com finalizacao anterior sem autoria registrada.");
                assertThat(flyway(schema, "7").info().current().getVersion().toString()).isEqualTo("7");
                try (var result = statement.executeQuery("select status, finalizada_em from compra")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(status);
                    assertThat(result.getTimestamp(2)).isEqualTo(timestamp ? Timestamp.from(AGORA) : null);
                }
                try (var result = statement.executeQuery(
                        "select count(*) from information_schema.columns where table_schema = '" + schema
                        + "' and table_name = 'compra' and column_name = 'finalizada_por_participante_compra_id'")) {
                    result.next();
                    assertThat(result.getInt(1)).isZero();
                }
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).target(target).load();
    }

    private void atualizar(Contexto c, String status, UUID autor, Instant instante) {
        jdbc.update("""
                update compra set status = ?, finalizada_por_participante_compra_id = ?, finalizada_em = ?
                where id = ?
                """, status, autor, instante == null ? null : Timestamp.from(instante), c.compra().getId());
    }

    private Contexto contexto() {
        var usuario = usuarios.saveAndFlush(Usuario.criar("Ana", UUID.randomUUID() + "@test.local", "hash", AGORA));
        var familia = familias.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), usuario, AGORA));
        var membro = membros.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, AGORA));
        var lista = listas.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, AGORA));
        var compra = compras.saveAndFlush(Compra.iniciar(lista, membro, AGORA));
        var participante = participantes.saveAndFlush(ParticipanteCompra.criarDireto(compra, membro, AGORA));
        return new Contexto(compra, participante);
    }

    private record Contexto(Compra compra, ParticipanteCompra participante) {}
}
