package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
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
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.application.ListaCompraForaDePreparacaoException;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.application.UsuarioNaoParticipaDaListaException;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.StatusListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
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
class IniciarCompraApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private AdicionarItemLista adicionarItemLista;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void iniciaCompraComSnapshotsDeParticipantesEItensAtivos() {
        Contexto contexto = criarContexto(true);
        MembroFamilia participanteRemovido = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);
        ParticipanteLista participacaoRemovida = participanteListaRepository.saveAndFlush(
                ParticipanteLista.criar(contexto.lista(), participanteRemovido, agora()));
        participacaoRemovida.sair(agora());
        participanteListaRepository.saveAndFlush(participacaoRemovida);
        ItemLista itemRemovido = criarItem(contexto, "Item removido", 3);
        itemRemovido.remover(agora());
        itemListaRepository.saveAndFlush(itemRemovido);
        ItemLista segundoItem = criarItem(contexto, "Segundo item", 2);

        ResultadoInicioCompra resultado = iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId());

        assertThat(resultado.criada()).isTrue();
        assertThat(resultado.compra().getStatus()).isEqualTo(StatusCompra.EM_ANDAMENTO);
        assertThat(campo(resultado.compra(), "listaCompra")).isSameAs(contexto.lista());
        assertThat(campo(resultado.compra(), "iniciadaPorMembroFamilia")).isSameAs(contexto.criador());
        assertThat(campo(resultado.compra(), "nomeListaSnapshot")).isEqualTo("Lista semanal");
        assertThat(campo(resultado.compra(), "categoriaSnapshot")).isEqualTo("SUPERMERCADO");
        assertThat(campo(resultado.compra(), "estabelecimentoSnapshot")).isEqualTo("Mercado Central");
        assertThat(contexto.lista().getStatus()).isEqualTo(StatusListaCompra.EM_COMPRA);

        List<ParticipanteCompra> participantes = participanteCompraRepository
                .findByCompra_IdOrderByGeradoEmAscIdAsc(id(resultado.compra()));
        assertThat(participantes).hasSize(1);
        assertThat(campo(participantes.getFirst(), "participanteListaOrigem")).isNotNull();
        assertThat(campo(participantes.getFirst(), "membroFamilia")).isSameAs(contexto.criador());

        List<ItemCompra> itens = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(id(resultado.compra()));
        assertThat(itens).hasSize(2);
        assertThat(itens).extracting(item -> campo(item, "ordemExibicao")).containsExactly(1, 2);
        assertThat(itens).allSatisfy(item -> {
            assertThat(campo(item, "itemListaOrigem")).isNotNull();
            assertThat(campo(item, "adicionadoDuranteCompra")).isEqualTo(false);
            assertThat(campo(item, "status")).isEqualTo(StatusItemCompra.PENDENTE);
        });
        assertThat(campo(itens.get(1), "descricaoSnapshot")).isEqualTo(segundoItem.getDescricao());
    }

    @Test
    void replaySequencialRetornaMesmaCompraSemDuplicarSnapshots() {
        Contexto contexto = criarContexto(true);

        ResultadoInicioCompra primeiraChamada = iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId());
        ResultadoInicioCompra replay = iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId());

        assertThat(replay.criada()).isFalse();
        assertThat(id(replay.compra())).isEqualTo(id(primeiraChamada.compra()));
        assertThat(participanteCompraRepository.findByCompra_IdOrderByGeradoEmAscIdAsc(id(primeiraChamada.compra())))
                .hasSize(1);
        assertThat(itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(id(primeiraChamada.compra())))
                .hasSize(1);
    }

    @Test
    void rejeitaListaSemItensAtivos() {
        Contexto contexto = criarContexto(false);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(ListaCompraSemItensException.class);
    }

    @Test
    void rejeitaListaSemParticipantesAtivos() {
        Contexto contexto = criarContexto(true);
        ParticipanteLista participacaoCriador = participanteListaRepository
                .findByListaCompra_IdAndMembroFamilia_Id(contexto.lista().getId(), contexto.criador().getId())
                .orElseThrow();
        participacaoCriador.sair(agora());
        participanteListaRepository.saveAndFlush(participacaoCriador);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(ListaCompraSemParticipantesException.class);
    }

    @Test
    void rejeitaMembroComumQueNaoParticipaDaLista() {
        Contexto contexto = criarContexto(true);
        MembroFamilia membro = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.MEMBRO);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                membro.getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(UsuarioNaoParticipaDaListaException.class);
    }

    @Test
    void rejeitaExecutorQueNaoParticipaDaListaMesmoQuandoAdministrador() {
        Contexto contexto = criarContexto(true);
        MembroFamilia administrador = criarMembro(contexto.familia(), "Bia", PapelMembroFamilia.ADMINISTRADOR);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                administrador.getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(UsuarioNaoParticipaDaListaException.class);
    }

    @Test
    void rejeitaUsuarioSemVinculoAtivoNaFamiliaDaLista() {
        Contexto contexto = criarContexto(true);
        Usuario usuarioExterno = usuarioRepository.saveAndFlush(Usuario.criar(
                "Externo", UUID.randomUUID() + "@test.local", "hash", agora()));

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                usuarioExterno.getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(MembroFamiliaInvalidoException.class);
    }

    @Test
    void rejeitaFamiliaIncompativelComALista() {
        Contexto contexto = criarContexto(true);
        Contexto outraFamilia = criarContexto(true);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), outraFamilia.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(ListaCompraNaoEncontradaException.class);
    }

    @Test
    void rejeitaListaEmCompraSemCompra() {
        Contexto contexto = criarContexto(true);
        atualizarStatusLista(contexto.lista(), StatusListaCompra.EM_COMPRA);

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(CompraListaInconsistenteException.class);
    }

    @Test
    void rejeitaListaEmPreparacaoComCompraExistente() {
        Contexto contexto = criarContexto(true);
        compraRepository.saveAndFlush(Compra.iniciar(contexto.lista(), contexto.criador(), agora()));

        assertThatThrownBy(() -> iniciarCompra.iniciar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId()))
                .isInstanceOf(CompraListaInconsistenteException.class);
    }

    @Test
    void rejeitaListaFinalizadaOuCancelada() {
        Contexto finalizada = criarContexto(true);
        atualizarStatusLista(finalizada.lista(), StatusListaCompra.FINALIZADA);
        assertThatThrownBy(() -> iniciarCompra.iniciar(
                finalizada.criador().getUsuario().getId(), finalizada.familia().getId(), finalizada.lista().getId()))
                .isInstanceOf(ListaCompraForaDePreparacaoException.class);

        Contexto cancelada = criarContexto(true);
        atualizarStatusLista(cancelada.lista(), StatusListaCompra.CANCELADA);
        assertThatThrownBy(() -> iniciarCompra.iniciar(
                cancelada.criador().getUsuario().getId(), cancelada.familia().getId(), cancelada.lista().getId()))
                .isInstanceOf(ListaCompraForaDePreparacaoException.class);
    }

    @Test
    void bloqueiaMutacoesDePreparacaoDepoisDoInicio() {
        Contexto contexto = criarContexto(true);
        iniciarCompra.iniciar(contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId());

        assertThatThrownBy(() -> adicionarItemLista.adicionar(
                contexto.criador().getUsuario().getId(), contexto.familia().getId(), contexto.lista().getId(),
                "Novo item", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null))
                .isInstanceOf(ListaCompraForaDePreparacaoException.class);
    }

    private Contexto criarContexto(boolean comItem) {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", UUID.randomUUID() + "@test.local", "hash", agora()));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), usuario, agora()));
        MembroFamilia criador = membroFamiliaRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, usuario, agora()));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista semanal", CategoriaCompra.SUPERMERCADO, "Mercado Central", criador, agora()));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, criador, agora()));
        Contexto contexto = new Contexto(familia, criador, lista);
        if (comItem) {
            criarItem(contexto, "Primeiro item", 1);
        }
        return contexto;
    }

    private MembroFamilia criarMembro(Familia familia, String nome, PapelMembroFamilia papel) {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(
                nome, UUID.randomUUID() + "@test.local", "hash", agora()));
        MembroFamilia membro = papel == PapelMembroFamilia.ADMINISTRADOR
                ? MembroFamilia.criarAdministrador(familia, usuario, agora())
                : MembroFamilia.criarMembro(familia, usuario, agora());
        return membroFamiliaRepository.saveAndFlush(membro);
    }

    private ItemLista criarItem(Contexto contexto, String descricao, int ordem) {
        return itemListaRepository.saveAndFlush(ItemLista.criar(
                contexto.lista(), descricao, BigDecimal.ONE, UnidadeMedida.UNIDADE, "Marca", "Obs", ordem,
                contexto.criador(), agora()));
    }

    private void atualizarStatusLista(ListaCompra lista, StatusListaCompra status) {
        jdbcTemplate.update("update lista_compra set status = ? where id = ?", status.name(), lista.getId());
        entityManager.clear();
    }

    private Instant agora() {
        return Instant.parse("2026-09-04T18:00:00Z");
    }

    private UUID id(Object entity) {
        return (UUID) campo(entity, "id");
    }

    private static Object campo(Object entity, String nome) {
        try {
            Field campo = entity.getClass().getDeclaredField(nome);
            campo.setAccessible(true);
            return campo.get(entity);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private record Contexto(Familia familia, MembroFamilia criador, ListaCompra lista) {
    }
}
