package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ColocarItemNoCarrinhoConcorrenciaIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private ColocarItemNoCarrinho colocarItemNoCarrinho;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void colocaMesmoItemConcorrentementeComUmaAlteracaoEUmaReexecucao() throws Exception {
        Contexto contexto = criarContexto();
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ResultadoColocarItemNoCarrinho> primeira = executor.submit(
                    () -> colocarNoCarrinho(contexto, contexto.primeiroUsuarioId(), prontas, iniciar));
            Future<ResultadoColocarItemNoCarrinho> segunda = executor.submit(
                    () -> colocarNoCarrinho(contexto, contexto.segundoUsuarioId(), prontas, iniciar));

            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            ResultadoColocarItemNoCarrinho resultadoA = primeira.get(20, TimeUnit.SECONDS);
            ResultadoColocarItemNoCarrinho resultadoB = segunda.get(20, TimeUnit.SECONDS);

            assertThat(List.of(resultadoA, resultadoB)).extracting(ResultadoColocarItemNoCarrinho::alterado)
                    .containsExactlyInAnyOrder(true, false);

            ItemCompra item = itemCompraRepository.findById(contexto.itemCompraId()).orElseThrow();
            assertThat(item.getStatus()).isEqualTo(StatusItemCompra.NO_CARRINHO);
            assertThat(item.getMarcadoEm()).isNotNull();
            assertThat(item.getMarcadoPorMembroFamilia().getId())
                    .isIn(contexto.primeiroMembroId(), contexto.segundoMembroId());
            assertThat(itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(contexto.compraId())).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from item_compra where compra_id = ? and status = 'NO_CARRINHO'",
                    Integer.class,
                    contexto.compraId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResultadoColocarItemNoCarrinho colocarNoCarrinho(
            Contexto contexto,
            UUID usuarioId,
            CountDownLatch prontas,
            CountDownLatch iniciar) throws InterruptedException {
        prontas.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("As chamadas concorrentes nao foram liberadas a tempo.");
        }
        return colocarItemNoCarrinho.executar(
                usuarioId, contexto.familiaId(), contexto.listaId(), contexto.itemCompraId());
    }

    private Contexto criarContexto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario primeiroUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), primeiroUsuario, agora));
        MembroFamilia primeiroMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, primeiroUsuario, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista semanal", CategoriaCompra.SUPERMERCADO, null, primeiroMembro, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, primeiroMembro, agora));

        Usuario segundoUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Bia", "bia-" + UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia segundoMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarMembro(familia, segundoUsuario, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, segundoMembro, agora));

        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, primeiroMembro, agora));

        UUID compraId = iniciarCompra.iniciar(primeiroUsuario.getId(), familia.getId(), lista.getId()).compra().getId();
        UUID itemCompraId = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId).getFirst().getId();

        return new Contexto(
                familia.getId(), lista.getId(), compraId, itemCompraId,
                primeiroUsuario.getId(), segundoUsuario.getId(), primeiroMembro.getId(), segundoMembro.getId());
    }

    private record Contexto(
            UUID familiaId,
            UUID listaId,
            UUID compraId,
            UUID itemCompraId,
            UUID primeiroUsuarioId,
            UUID segundoUsuarioId,
            UUID primeiroMembroId,
            UUID segundoMembroId) {
    }
}
