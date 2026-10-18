package com.mercadeira.api.lista.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.api.ListaCompraController;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
class ReferenciaPrecoItensIntegrationTests {
    private static final Instant INICIO = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant FINALIZADA = INICIO.plusSeconds(3600);
    private static final UUID ID_MENOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_MAIOR = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Container @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private ReferenciaPrecoItensRepository referencias;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private FamiliaRepository familias;
    @Autowired private MembroFamiliaRepository membros;
    @Autowired private ListaCompraRepository listas;
    @Autowired private ParticipanteListaRepository participantesLista;
    @Autowired private ItemListaRepository itensLista;
    @Autowired private CompraRepository compras;
    @Autowired private ParticipanteCompraRepository participantesCompra;
    @Autowired private ItemCompraRepository itensCompra;
    @Autowired private ListaCompraController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private Contexto contexto;
    private ListaCompra alvo;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        contexto = criarContexto();
        alvo = lista(contexto, CategoriaCompra.SUPERMERCADO);
        participantesLista.save(ParticipanteLista.criar(alvo, contexto.membro(), INICIO));
        itemAlvo("Arroz", "2", UnidadeMedida.UNIDADE);
        var jwt = Jwt.withTokenValue("teste").header("alg", "none").subject(contexto.usuario().getId().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usaUmLancamentoMesmoComColunasLegadasDivergentes() {
        var item = historico();
        item.registrarDadosDaCompra(new BigDecimal("999.00"), new BigDecimal("99"));
        lancamento(item, "4.90", "3", INICIO, UUID.randomUUID());

        var referencia = buscar().getFirst();
        assertThat(referencia.precoUnitario()).isEqualByComparingTo("4.90");
        assertThat(referencia.data()).isEqualTo(FINALIZADA);
        assertThat(referencia.estabelecimento()).isEqualTo("Mercado teste");
    }

    @Test
    void usaLancamentoMaisRecenteDoMesmoItemSemMediaMaximoMinimoOuSubtotal() {
        var item = historico();
        lancamento(item, "0.50", "1", INICIO.minusSeconds(1), UUID.randomUUID());
        lancamento(item, "90.00", "1", INICIO, ID_MAIOR);
        lancamento(item, "4.90", "7", INICIO.plusSeconds(1), ID_MENOR);

        assertThat(buscar().getFirst().precoUnitario()).isEqualByComparingTo("4.90");
    }

    @Test
    void priorizaCompraFinalizadaMaisRecentementeAntesDaDataDoLancamento() {
        var antiga = historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA);
        var recente = historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA.plusSeconds(1));
        lancamento(antiga, "99.00", "1", INICIO.plusSeconds(100), ID_MAIOR);
        lancamento(recente, "5.00", "1", INICIO, ID_MENOR);

        assertThat(buscar().getFirst().precoUnitario()).isEqualByComparingTo("5.00");
        assertThat(buscar().getFirst().data()).isEqualTo(FINALIZADA.plusSeconds(1));
    }

    @Test
    void desempataComprasFinalizadasNaMesmaDataPorCriadoEmDoLancamento() {
        var primeiro = historico();
        var segundo = historico();
        lancamento(primeiro, "6.00", "1", INICIO.plusSeconds(1), ID_MENOR);
        lancamento(segundo, "99.00", "1", INICIO, ID_MAIOR);

        assertThat(buscar().getFirst().precoUnitario()).isEqualByComparingTo("6.00");
    }

    @Test
    void desempataPorIdDoLancamentoEntreItensDistintosDeterministicamente() {
        var itens = List.of(historico(), historico()).stream()
                .sorted(java.util.Comparator.comparing(item -> item.getId().toString())).toList();
        // O maior ID do lancamento fica no menor ID de item, contrariando a antiga ordenacao por ic.id.
        lancamento(itens.getFirst(), "7.00", "1", INICIO, ID_MAIOR);
        lancamento(itens.getLast(), "99.00", "1", INICIO, ID_MENOR);

        assertThat(buscar()).hasSize(1);
        assertThat(buscar().getFirst().precoUnitario()).isEqualByComparingTo("7.00");
    }

    @ParameterizedTest
    @EnumSource(value = StatusItemCompra.class, names = { "PENDENTE", "REMOCAO_SOLICITADA", "REMOVIDO" })
    void ignoraItensForaDoCarrinho(StatusItemCompra status) {
        var item = historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                status, StatusCompra.FINALIZADA, FINALIZADA);
        lancamento(item, "99.00", "1", INICIO, UUID.randomUUID());

        assertThat(buscar()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatusCompra.class, names = { "EM_ANDAMENTO", "CANCELADA" })
    void ignoraComprasNaoFinalizadas(StatusCompra status) {
        var item = historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, status, FINALIZADA);
        lancamento(item, "99.00", "1", INICIO, UUID.randomUUID());

        assertThat(buscar()).isEmpty();
    }

    @Test
    void isolaFamiliaCategoriaDescricaoEUnidade() {
        lancamento(historico(criarContexto(), CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "99", "1", INICIO, UUID.randomUUID());
        lancamento(historico(contexto, CategoriaCompra.OUTROS, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "99", "1", INICIO, UUID.randomUUID());
        lancamento(historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.KG,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "99", "1", INICIO, UUID.randomUUID());
        lancamento(historico(contexto, CategoriaCompra.SUPERMERCADO, "Feijao", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "99", "1", INICIO, UUID.randomUUID());

        assertThat(buscar()).isEmpty();
        lancamento(historico(), "4.90", "1", INICIO, UUID.randomUUID());
        assertThat(buscar()).singleElement().satisfies(r -> assertThat(r.precoUnitario()).isEqualByComparingTo("4.90"));
    }

    @Test
    void normalizaDescricaoEmLotePreservandoItensDuplicadosEUnidadesNulas() {
        itemAlvo("  ARROZ  ", "3", UnidadeMedida.UNIDADE);
        itemAlvo("  arroz \t integral  ", "1", null);
        lancamento(historico(), "4.90", "1", INICIO, UUID.randomUUID());
        lancamento(historico(contexto, CategoriaCompra.SUPERMERCADO, " ARROZ   integral ", null,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "7.00", "1", INICIO, UUID.randomUUID());

        assertThat(buscar()).hasSize(3).extracting(r -> r.precoUnitario().stripTrailingZeros())
                .containsExactlyInAnyOrder(new BigDecimal("4.9"), new BigDecimal("4.9"), new BigDecimal("7"));
        assertThat(buscar()).extracting(ReferenciaPrecoItensRepository.Referencia::itemListaId).doesNotHaveDuplicates();
    }

    @Test
    void naoUsaColunasLegadasComoFallbackESemHistoricoMantemReferenciaNula() throws Exception {
        historico().registrarDadosDaCompra(new BigDecimal("999"), BigDecimal.ONE);
        em.flush();

        assertThat(buscar()).isEmpty();
        mvc.perform(get(url() + "/itens")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenciaPreco").isEmpty());
        mvc.perform(get(url())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa.valor").value(0))
                .andExpect(jsonPath("$.estimativa.itensComReferencia").value(0))
                .andExpect(jsonPath("$.estimativa.totalItens").value(1));
    }

    @Test
    void estimativaMantemContratoEUsaQuantidadePlanejadaVezesUltimoPrecoUnitario() throws Exception {
        itemAlvo("Feijao", "3", UnidadeMedida.KG);
        itemAlvo("Sem historico", "5", UnidadeMedida.UNIDADE);
        itemAlvo("Arroz", null, UnidadeMedida.UNIDADE);
        var removido = itemAlvo("Removido da lista", "10", UnidadeMedida.UNIDADE);
        removido.remover(INICIO.plusSeconds(1));
        var arroz = historico();
        lancamento(arroz, "99", "1", INICIO, UUID.randomUUID());
        lancamento(arroz, "4.90", "7", INICIO.plusSeconds(1), UUID.randomUUID());
        lancamento(historico(contexto, CategoriaCompra.SUPERMERCADO, "Feijao", UnidadeMedida.KG,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA), "8.00", "10", INICIO, UUID.randomUUID());
        em.flush();

        mvc.perform(get(url() + "/itens")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].referenciaPreco.precoUnitario").value(4.90))
                .andExpect(jsonPath("$[0].referenciaPreco.data").value(FINALIZADA.toString()))
                .andExpect(jsonPath("$[0].referenciaPreco.estabelecimento").value("Mercado teste"))
                .andExpect(jsonPath("$[2].referenciaPreco").isEmpty());
        mvc.perform(get(url())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa.valor").value(33.80))
                .andExpect(jsonPath("$.estimativa.itensComReferencia").value(2))
                .andExpect(jsonPath("$.estimativa.totalItens").value(4));
    }

    private List<ReferenciaPrecoItensRepository.Referencia> buscar() {
        em.flush();
        return referencias.buscar(contexto.familia().getId(), alvo.getId(), alvo.getCategoria().name());
    }

    private String url() {
        return "/api/familias/" + contexto.familia().getId() + "/listas/" + alvo.getId();
    }

    private ItemLista itemAlvo(String descricao, String quantidade, UnidadeMedida unidade) {
        int ordem = itensLista.findByListaCompra_IdAndRemovidoEmIsNullOrderByOrdemExibicaoAscIdAsc(alvo.getId()).size() + 1;
        return itensLista.saveAndFlush(ItemLista.criar(alvo, descricao, quantidade == null ? null : new BigDecimal(quantidade),
                unidade, null, null, ordem, contexto.membro(), INICIO));
    }

    private ItemCompra historico() {
        return historico(contexto, CategoriaCompra.SUPERMERCADO, "Arroz", UnidadeMedida.UNIDADE,
                StatusItemCompra.NO_CARRINHO, StatusCompra.FINALIZADA, FINALIZADA);
    }

    private ItemCompra historico(Contexto c, CategoriaCompra categoria, String descricao, UnidadeMedida unidade,
            StatusItemCompra statusItem, StatusCompra statusCompra, Instant finalizadaEm) {
        var lista = lista(c, categoria);
        var compra = compras.saveAndFlush(Compra.iniciar(lista, c.membro(), INICIO));
        var participante = participantesCompra.saveAndFlush(ParticipanteCompra.criarDireto(compra, c.membro(), INICIO));
        var item = ItemCompra.criarDuranteCompra(compra, participante, descricao, BigDecimal.ONE, unidade, null, null, 1, INICIO);
        if (statusItem != StatusItemCompra.PENDENTE) item.colocarNoCarrinho(c.membro(), INICIO);
        if (statusItem == StatusItemCompra.REMOCAO_SOLICITADA || statusItem == StatusItemCompra.REMOVIDO)
            item.solicitarRemocao(c.membro(), INICIO);
        if (statusItem == StatusItemCompra.REMOVIDO) item.aprovarRemocao(c.membro(), INICIO);
        itensCompra.saveAndFlush(item);
        if (statusCompra == StatusCompra.FINALIZADA) compra.finalizar(participante, finalizadaEm);
        em.flush();
        if (statusCompra == StatusCompra.CANCELADA) jdbc.update("update compra set status='CANCELADA' where id=?", compra.getId());
        return item;
    }

    private void lancamento(ItemCompra item, String preco, String quantidade, Instant criadoEm, UUID id) {
        em.flush();
        // IDs e datas controlados para verificar a ordenacao real do PostgreSQL, inclusive entre itens distintos.
        jdbc.update("""
                insert into lancamento_item_compra (id, item_compra_id, preco_unitario, quantidade, criado_em, atualizado_em)
                values (?, ?, ?, ?, ?, ?)
                """, id, item.getId(), new BigDecimal(preco), new BigDecimal(quantidade),
                Timestamp.from(criadoEm), Timestamp.from(criadoEm));
    }

    private ListaCompra lista(Contexto c, CategoriaCompra categoria) {
        return listas.saveAndFlush(ListaCompra.criar(c.familia(), "Lista", categoria, "Mercado teste", c.membro(), INICIO));
    }

    private Contexto criarContexto() {
        var usuario = usuarios.saveAndFlush(Usuario.criar("Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", INICIO));
        var familia = familias.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), usuario, INICIO));
        var membro = membros.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, INICIO));
        return new Contexto(usuario, familia, membro);
    }

    private record Contexto(Usuario usuario, Familia familia, MembroFamilia membro) { }
}
