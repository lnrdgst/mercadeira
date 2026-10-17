package com.mercadeira.api.compra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class LancamentoItemCompraV25IntegrationTests {

    @Container @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private UsuarioRepository usuarios;
    @Autowired private FamiliaRepository familias;
    @Autowired private MembroFamiliaRepository membros;
    @Autowired private ListaCompraRepository listas;
    @Autowired private CompraRepository compras;
    @Autowired private ParticipanteCompraRepository participantes;
    @Autowired private ItemCompraRepository itens;
    @Autowired private LancamentoItemCompraRepository lancamentos;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void persisteEBuscaLancamentosOrdenadosPorItemCompra() {
        var contexto = criarContexto();
        var segundo = contexto.comPreco().adicionarLancamento(new BigDecimal("5.00"), BigDecimal.ONE,
                contexto.marcadoEm().plusSeconds(1));
        var primeiro = contexto.comPreco().adicionarLancamento(new BigDecimal("4.90"), BigDecimal.ONE,
                contexto.marcadoEm());
        lancamentos.saveAllAndFlush(java.util.List.of(segundo, primeiro));

        var encontrados = lancamentos.findByItemCompra_IdOrderByCriadoEmAscIdAsc(contexto.comPreco().getId());

        assertThat(encontrados).extracting(lancamento -> lancamento.getPrecoUnitario().toPlainString())
                .containsExactly("4.90", "5.00");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migraV25FazBackfillSomenteDosValoresFinanceirosCompletosEPreservaConstraints() {
        var contexto = criarContexto();
        String schema = "v25_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()).schemas(schema).defaultSchema(schema).target("24").load().migrate();
        copiarContexto(schema, contexto);

        Flyway.configure().dataSource(postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()).schemas(schema).defaultSchema(schema).target("25").load().migrate();

        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".lancamento_item_compra", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select preco_unitario from " + schema
                + ".lancamento_item_compra where item_compra_id=?", BigDecimal.class, contexto.comPreco().getId()))
                .isEqualByComparingTo("4.90");
        assertThat(jdbc.queryForObject("select quantidade from " + schema
                + ".lancamento_item_compra where item_compra_id=?", BigDecimal.class, contexto.comPreco().getId()))
                .isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select criado_em from " + schema
                + ".lancamento_item_compra where item_compra_id=?", Timestamp.class, contexto.comPreco().getId()).toInstant())
                .isEqualTo(contexto.marcadoEm());
        assertThat(jdbc.queryForObject("select atualizado_em from " + schema
                + ".lancamento_item_compra where item_compra_id=?", Timestamp.class, contexto.comPreco().getId()).toInstant())
                .isEqualTo(contexto.marcadoEm());
        assertThat(jdbc.queryForObject("select coalesce(sum(preco_unitario * quantidade), 0) from "
                + schema + ".lancamento_item_compra", BigDecimal.class)).isEqualByComparingTo("9.8000");
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".lancamento_item_compra where item_compra_id in (?, ?)", Integer.class,
                contexto.semPreco().getId(), contexto.parcial().getId())).isZero();
        assertThat(jdbc.queryForObject("select indexname from pg_indexes where schemaname=? and indexname=?", String.class,
                schema, "idx_lancamento_item_compra_item_criado_em")).isEqualTo("idx_lancamento_item_compra_item_criado_em");

        Instant agora = contexto.marcadoEm();
        assertThatThrownBy(() -> inserir(schema, UUID.randomUUID(), contexto.comPreco().getId(), null, BigDecimal.ONE, agora))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir(schema, UUID.randomUUID(), contexto.comPreco().getId(), BigDecimal.ONE, null, agora))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir(schema, UUID.randomUUID(), contexto.comPreco().getId(), BigDecimal.ZERO, BigDecimal.ONE, agora))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir(schema, UUID.randomUUID(), contexto.comPreco().getId(), BigDecimal.ONE, BigDecimal.ZERO, agora))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir(schema, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE, agora))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void inserir(String schema, UUID id, UUID itemCompraId, BigDecimal preco, BigDecimal quantidade, Instant agora) {
        jdbc.update("insert into " + schema + ".lancamento_item_compra (id,item_compra_id,preco_unitario,quantidade,criado_em,atualizado_em) values (?,?,?,?,?,?)",
                id, itemCompraId, preco, quantidade, Timestamp.from(agora), Timestamp.from(agora));
    }

    private Contexto criarContexto() {
        Instant iniciadoEm = Instant.parse("2026-10-17T10:00:00Z");
        Instant marcadoEm = Instant.parse("2026-10-17T10:05:00Z");
        var usuario = usuarios.saveAndFlush(Usuario.criar("Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", iniciadoEm));
        var familia = familias.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), usuario, iniciadoEm));
        var membro = membros.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, iniciadoEm));
        var lista = listas.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, iniciadoEm));
        var compra = compras.saveAndFlush(Compra.iniciar(lista, membro, iniciadoEm));
        var participante = participantes.saveAndFlush(ParticipanteCompra.criarDireto(compra, membro, iniciadoEm));

        var comPreco = ItemCompra.criarDuranteCompra(compra, participante, "Produto A", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 1, iniciadoEm);
        comPreco.colocarNoCarrinho(membro, marcadoEm);
        comPreco.registrarDadosDaCompra(new BigDecimal("4.90"), new BigDecimal("2"));
        var semPreco = ItemCompra.criarDuranteCompra(compra, participante, "Produto B", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 2, iniciadoEm);
        var parcial = ItemCompra.criarDuranteCompra(compra, participante, "Produto C", BigDecimal.ONE,
                UnidadeMedida.UNIDADE, null, null, 3, iniciadoEm);
        parcial.colocarNoCarrinho(membro, marcadoEm);
        parcial.registrarDadosDaCompra(null, new BigDecimal("2"));
        itens.saveAllAndFlush(java.util.List.of(comPreco, semPreco, parcial));
        return new Contexto(usuario, familia, membro, lista, compra, participante, comPreco, semPreco, parcial, marcadoEm);
    }

    private void copiarContexto(String schema, Contexto contexto) {
        copiar(schema, "usuario", "id", contexto.usuario().getId());
        copiar(schema, "familia", "id", contexto.familia().getId());
        copiar(schema, "membro_familia", "id", contexto.membro().getId());
        copiar(schema, "lista_compra", "id", contexto.lista().getId());
        copiar(schema, "compra", "id", contexto.compra().getId());
        copiar(schema, "participante_compra", "id", contexto.participante().getId());
        for (var item : java.util.List.of(contexto.comPreco(), contexto.semPreco(), contexto.parcial())) {
            copiar(schema, "item_compra", "id", item.getId());
        }
    }

    private void copiar(String schema, String tabela, String coluna, UUID id) {
        String colunas = jdbc.queryForObject("""
                select string_agg(quote_ident(column_name), ', ' order by ordinal_position)
                from information_schema.columns where table_schema='public' and table_name=?
                """, String.class, tabela);
        jdbc.update("insert into " + schema + "." + tabela + " (" + colunas + ") select " + colunas
                + " from public." + tabela + " where " + coluna + "=?", id);
    }

    private record Contexto(Usuario usuario, Familia familia, MembroFamilia membro, ListaCompra lista, Compra compra,
            ParticipanteCompra participante, ItemCompra comPreco, ItemCompra semPreco, ItemCompra parcial, Instant marcadoEm) { }
}
