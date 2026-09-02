package com.mercadeira.api.lista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.application.AdicionarParticipanteLista;
import com.mercadeira.api.lista.application.ConsultarListaCompra;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.application.CriadorListaNaoPodeSerRemovidoException;
import com.mercadeira.api.lista.application.EditarItemLista;
import com.mercadeira.api.lista.application.ListaCompraForaDePreparacaoException;
import com.mercadeira.api.lista.application.ListarItensLista;
import com.mercadeira.api.lista.application.ListarListasFamilia;
import com.mercadeira.api.lista.application.ListarParticipantesLista;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.application.OrdemItensInvalidaException;
import com.mercadeira.api.lista.application.ReordenarItensLista;
import com.mercadeira.api.lista.application.RemoverItemLista;
import com.mercadeira.api.lista.application.RemoverParticipanteLista;
import com.mercadeira.api.lista.application.UsuarioNaoParticipaDaListaException;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
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
@Rollback
class ListaCompraApplicationTests {

    private static final String JWT_TEST_SECRET = Base64.getEncoder().encodeToString(
            "segredo-exclusivo-de-teste-com-32-bytes-ou-mais".getBytes(StandardCharsets.UTF_8));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> JWT_TEST_SECRET);
    }

    @Autowired private CadastrarUsuario cadastrarUsuario;
    @Autowired private CriarFamilia criarFamilia;
    @Autowired private CriarListaCompra criarLista;
    @Autowired private ListarListasFamilia listarListas;
    @Autowired private ConsultarListaCompra consultarLista;
    @Autowired private AdicionarParticipanteLista adicionarParticipante;
    @Autowired private RemoverParticipanteLista removerParticipante;
    @Autowired private ListarParticipantesLista listarParticipantes;
    @Autowired private AdicionarItemLista adicionarItem;
    @Autowired private EditarItemLista editarItem;
    @Autowired private RemoverItemLista removerItem;
    @Autowired private ListarItensLista listarItens;
    @Autowired private ReordenarItensLista reordenarItens;
    @Autowired private MembroFamiliaRepository membroRepository;
    @Autowired private ParticipanteListaRepository participanteRepository;
    @Autowired private ItemListaRepository itemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void criaListaEmPreparacaoComCriadorParticipanteEPersisteEnumsComoTexto() {
        Usuario usuario = usuario("Ana");
        criarFamilia.criar(usuario.getId(), "Casa da Ana");

        ListaCompra lista = criarLista.criar(usuario.getId(), membroAtivo(usuario).getFamilia().getId(), "Compras", CategoriaCompra.SUPERMERCADO, null);

        assertThat(lista.getStatus()).isEqualTo(StatusListaCompra.EM_PREPARACAO);
        assertThat(participanteRepository.findByListaCompra_IdAndSaiuEmIsNull(lista.getId()))
                .extracting(p -> p.getMembroFamilia().getId()).containsExactly(lista.getCriadaPorMembroFamilia().getId());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("select categoria from lista_compra where id = ?", String.class, lista.getId()))
                .isEqualTo("SUPERMERCADO");
        ItemLista item = adicionarItem.adicionar(usuario.getId(), lista.getFamilia().getId(), lista.getId(), "Arroz", new BigDecimal("2.500"), UnidadeMedida.KG, null, null);
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("select unidade_medida from item_lista where id = ?", String.class, item.getId()))
                .isEqualTo("KG");
    }

    @Test
    void impedeCriacaoSemFamiliaAtiva() {
        assertThatThrownBy(() -> criarLista.criar(usuario("Sem familia").getId(), UUID.randomUUID(), "Lista", CategoriaCompra.OUTROS, null))
                .isInstanceOf(MembroFamiliaInvalidoException.class);
    }

    @Test
    void listaApenasListasDaPropriaFamiliaEImpedeConsultaExterna() {
        Usuario ana = usuario("Ana"); criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra listaAna = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Ana", CategoriaCompra.OUTROS, null);
        Usuario bia = usuario("Bia"); criarFamilia.criar(bia.getId(), "Casa Bia");
        criarLista.criar(bia.getId(), membroAtivo(bia).getFamilia().getId(), "Bia", CategoriaCompra.OUTROS, null);

        assertThat(listarListas.listar(ana.getId(), listaAna.getFamilia().getId())).extracting(ListaCompra::getId).containsExactly(listaAna.getId());
        assertThatThrownBy(() -> consultarLista.consultar(bia.getId(), listaAna.getFamilia().getId(), listaAna.getId()))
                .isInstanceOf(MembroFamiliaInvalidoException.class);
    }

    @Test
    void utilizaFamiliaExplicitaQuandoUsuarioParticipaDeMultiplasFamilias() {
        Usuario ana = usuario("Ana");
        Familia familiaA = criarFamilia.criar(ana.getId(), "Casa Ana");
        Familia familiaB = criarFamilia.criar(ana.getId(), "Casa Pais");

        ListaCompra listaA = criarLista.criar(ana.getId(), familiaA.getId(), "Lista A", CategoriaCompra.OUTROS, null);
        ListaCompra listaB = criarLista.criar(ana.getId(), familiaB.getId(), "Lista B", CategoriaCompra.OUTROS, null);

        assertThat(listarListas.listar(ana.getId(), familiaA.getId()))
                .extracting(ListaCompra::getId).containsExactly(listaA.getId());
        assertThat(listarListas.listar(ana.getId(), familiaB.getId()))
                .extracting(ListaCompra::getId).containsExactly(listaB.getId());
    }

    @Test
    void adicionaParticipanteDaMesmaFamiliaEImpedeMembroExterno() {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        MembroFamilia bia = adicionarMembro(familia, usuario("Bia"));
        Usuario externa = usuario("Externa"); criarFamilia.criar(externa.getId(), "Casa externa");
        MembroFamilia membroExterno = membroAtivo(externa);

        adicionarParticipante.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), bia.getId());

        assertThat(listarParticipantes.listar(ana.getId(), lista.getFamilia().getId(), lista.getId())).extracting(p -> p.getMembroFamilia().getId()).contains(bia.getId());
        assertThatThrownBy(() -> adicionarParticipante.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), membroExterno.getId()))
                .isInstanceOf(MembroFamiliaInvalidoException.class);
    }

    @Test
    void removeParticipanteLogicamenteEReativaMesmoVinculo() {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        MembroFamilia bia = adicionarMembro(familia, usuario("Bia"));
        ParticipanteLista participante = adicionarParticipante.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), bia.getId());

        removerParticipante.remover(ana.getId(), lista.getFamilia().getId(), lista.getId(), bia.getId());
        adicionarParticipante.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), bia.getId());

        ParticipanteLista reativado = participanteRepository.findByListaCompra_IdAndMembroFamilia_Id(lista.getId(), bia.getId()).orElseThrow();
        assertThat(reativado.getId()).isEqualTo(participante.getId());
        assertThat(reativado.getSaiuEm()).isNull();
    }

    @Test
    void impedeRemocaoDoCriador() {
        Usuario ana = usuario("Ana"); criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        assertThatThrownBy(() -> removerParticipante.remover(ana.getId(), lista.getFamilia().getId(), lista.getId(), lista.getCriadaPorMembroFamilia().getId()))
                .isInstanceOf(CriadorListaNaoPodeSerRemovidoException.class);
    }

    @Test
    void adicionaEEditaItemComoParticipanteMasImpedeNaoParticipante() {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        Usuario biaUsuario = usuario("Bia");
        MembroFamilia bia = adicionarMembro(familia, biaUsuario);
        jdbcTemplate.update("update membro_familia set papel = 'ADMINISTRADOR' where id = ?", bia.getId());
        entityManager.clear();

        // Ser administradora da familia nao substitui a participacao ativa na lista.
        assertThatThrownBy(() -> adicionarItem.adicionar(biaUsuario.getId(), lista.getFamilia().getId(), lista.getId(), "Leite", null, null, null, null))
                .isInstanceOf(UsuarioNaoParticipaDaListaException.class);
        ItemLista item = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Leite", null, null, null, null);
        editarItem.editar(ana.getId(), lista.getFamilia().getId(), lista.getId(), item.getId(), "Leite integral", BigDecimal.ONE, UnidadeMedida.LITRO, "Marca", "Gelado");
        assertThat(item.getDescricao()).isEqualTo("Leite integral");
        assertThat(item.getUnidadeMedida()).isEqualTo(UnidadeMedida.LITRO);
    }

    @Test
    void removeItemLogicamenteENaoOListaEntreAtivos() {
        Usuario ana = usuario("Ana"); criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        ItemLista item = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Leite", null, null, null, null);
        removerItem.remover(ana.getId(), lista.getFamilia().getId(), lista.getId(), item.getId());
        assertThat(item.getRemovidoEm()).isNotNull();
        assertThat(listarItens.listar(ana.getId(), lista.getFamilia().getId(), lista.getId())).isEmpty();
    }

    @Test
    void atribuiOrdemDeInclusaoEReordenaItens() {
        Usuario ana = usuario("Ana"); criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        ItemLista primeiro = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Primeiro", null, null, null, null);
        ItemLista segundo = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Segundo", null, null, null, null);
        assertThat(listarItens.listar(ana.getId(), lista.getFamilia().getId(), lista.getId())).extracting(ItemLista::getId).containsExactly(primeiro.getId(), segundo.getId());
        reordenarItens.reordenar(ana.getId(), lista.getFamilia().getId(), lista.getId(), List.of(segundo.getId(), primeiro.getId()));
        assertThat(listarItens.listar(ana.getId(), lista.getFamilia().getId(), lista.getId())).extracting(ItemLista::getId).containsExactly(segundo.getId(), primeiro.getId());
    }

    @Test
    void rejeitaReordenacaoIncompletaDuplicadaOuComIdInvalido() {
        Usuario ana = usuario("Ana"); criarFamilia.criar(ana.getId(), "Casa Ana");
        ListaCompra lista = criarLista.criar(ana.getId(), membroAtivo(ana).getFamilia().getId(), "Lista", CategoriaCompra.OUTROS, null);
        ItemLista primeiro = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Primeiro", null, null, null, null);
        ItemLista segundo = adicionarItem.adicionar(ana.getId(), lista.getFamilia().getId(), lista.getId(), "Segundo", null, null, null, null);
        assertThatThrownBy(() -> reordenarItens.reordenar(ana.getId(), lista.getFamilia().getId(), lista.getId(), List.of(primeiro.getId()))).isInstanceOf(OrdemItensInvalidaException.class);
        assertThatThrownBy(() -> reordenarItens.reordenar(ana.getId(), lista.getFamilia().getId(), lista.getId(), List.of(primeiro.getId(), primeiro.getId()))).isInstanceOf(OrdemItensInvalidaException.class);
        assertThatThrownBy(() -> reordenarItens.reordenar(ana.getId(), lista.getFamilia().getId(), lista.getId(), List.of(primeiro.getId(), UUID.randomUUID()))).isInstanceOf(OrdemItensInvalidaException.class);
        assertThat(segundo.getId()).isNotNull();
    }

    @Test
    void impedeAlteracoesQuandoListaNaoEstaEmPreparacao() {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "Casa Ana");
        MembroFamilia criador = membroAtivo(ana);
        UUID listaId = inserirListaEmCompra(familia, criador);
        assertThatThrownBy(() -> adicionarItem.adicionar(ana.getId(), familia.getId(), listaId, "Item", null, null, null, null))
                .isInstanceOf(ListaCompraForaDePreparacaoException.class);
    }

    private Usuario usuario(String nome) { return cadastrarUsuario.cadastrar(nome, nome.toLowerCase() + UUID.randomUUID() + "@test.local", "senha-original"); }
    private MembroFamilia membroAtivo(Usuario usuario) { return membroRepository.findByUsuario_IdAndStatusOrderByFamilia_NomeAsc(usuario.getId(), StatusMembroFamilia.ATIVO).getFirst(); }
    private MembroFamilia adicionarMembro(Familia familia, Usuario usuario) {
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', ?, ?)",
                UUID.randomUUID(), familia.getId(), usuario.getId(), timestamp(Instant.now()), timestamp(Instant.now()));
        entityManager.clear();
        return membroAtivo(usuario);
    }
    private UUID inserirListaEmCompra(Familia familia, MembroFamilia criador) {
        entityManager.flush(); UUID id = UUID.randomUUID(); Instant agora = Instant.now();
        jdbcTemplate.update("insert into lista_compra (id, familia_id, nome, categoria, status, criada_por_membro_familia_id, criada_em, atualizada_em) values (?, ?, ?, ?, 'EM_COMPRA', ?, ?, ?)",
                id, familia.getId(), "Em compra", "OUTROS", criador.getId(), timestamp(agora), timestamp(agora));
        jdbcTemplate.update("insert into participante_lista (id, lista_compra_id, membro_familia_id, entrou_em) values (?, ?, ?, ?)", UUID.randomUUID(), id, criador.getId(), timestamp(agora));
        entityManager.clear(); return id;
    }
    private Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }
}

