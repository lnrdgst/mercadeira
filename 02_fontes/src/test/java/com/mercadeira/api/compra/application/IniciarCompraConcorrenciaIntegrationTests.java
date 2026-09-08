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

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.compra.repository.ParticipanteCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class IniciarCompraConcorrenciaIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;

    @Test
    void iniciaMesmaListaConcorrentementeComUmaCriacaoEUmaReexecucao() throws Exception {
        Contexto contexto = criarContexto();
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ResultadoInicioCompra> primeira = executor.submit(() -> iniciarCompra(contexto, prontas, iniciar));
            Future<ResultadoInicioCompra> segunda = executor.submit(() -> iniciarCompra(contexto, prontas, iniciar));

            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            ResultadoInicioCompra resultadoA = primeira.get(20, TimeUnit.SECONDS);
            ResultadoInicioCompra resultadoB = segunda.get(20, TimeUnit.SECONDS);

            assertThat(List.of(resultadoA, resultadoB)).extracting(ResultadoInicioCompra::criada)
                    .containsExactlyInAnyOrder(true, false);
            assertThat(resultadoA.compra().getId()).isEqualTo(resultadoB.compra().getId());

            Compra compra = compraRepository.findByListaCompra_Id(contexto.listaId()).orElseThrow();
            assertThat(compraRepository.count()).isEqualTo(1);
            assertThat(listaCompraRepository.findById(contexto.listaId()).orElseThrow().getStatus())
                    .isEqualTo(StatusListaCompra.EM_COMPRA);

            List<ParticipanteCompra> participantes = participanteCompraRepository
                    .findByCompra_IdOrderByGeradoEmAscIdAsc(compra.getId());
            assertThat(participantes).hasSize(2);
            assertThat(participantes).extracting(participante -> participante.getMembroFamilia().getId())
                    .doesNotHaveDuplicates();

            List<ItemCompra> itens = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compra.getId());
            assertThat(itens).hasSize(2);
            assertThat(itens).extracting(item -> item.getItemListaOrigem().getId()).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResultadoInicioCompra iniciarCompra(Contexto contexto, CountDownLatch prontas, CountDownLatch iniciar)
            throws InterruptedException {
        prontas.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("As chamadas concorrentes nao foram liberadas a tempo.");
        }
        return iniciarCompra.iniciar(contexto.usuarioId(), contexto.familiaId(), contexto.listaId());
    }

    private Contexto criarContexto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario criador = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), criador, agora));
        MembroFamilia membroCriador = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, criador, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista semanal", CategoriaCompra.SUPERMERCADO, "Mercado Central", membroCriador, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membroCriador, agora));

        Usuario segundoUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Bia", "bia-" + UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia segundoMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarMembro(familia, segundoUsuario, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, segundoMembro, agora));

        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, "Marca A", "", 1, membroCriador, agora));
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Feijao", BigDecimal.TWO, UnidadeMedida.UNIDADE, "Marca B", "", 2, membroCriador, agora));

        return new Contexto(criador.getId(), familia.getId(), lista.getId());
    }

    private record Contexto(UUID usuarioId, UUID familiaId, UUID listaId) {
    }
}
