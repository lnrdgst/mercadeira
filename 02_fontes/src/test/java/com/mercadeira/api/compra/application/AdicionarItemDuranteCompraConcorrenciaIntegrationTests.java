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
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.compra.domain.StatusItemCompra;
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
class AdicionarItemDuranteCompraConcorrenciaIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private AdicionarItemDuranteCompra adicionarItemDuranteCompra;
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
    void adicionaDoisItensConcorrentementeComOrdensSequenciais() throws Exception {
        Contexto contexto = criarContexto();
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ItemCompra> primeira = executor.submit(() -> adicionar(
                    contexto, contexto.primeiroUsuarioId(), "Cafe", prontas, iniciar));
            Future<ItemCompra> segunda = executor.submit(() -> adicionar(
                    contexto, contexto.segundoUsuarioId(), "Leite", prontas, iniciar));

            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            ItemCompra itemA = primeira.get(20, TimeUnit.SECONDS);
            ItemCompra itemB = segunda.get(20, TimeUnit.SECONDS);

            assertThat(itemA.getId()).isNotEqualTo(itemB.getId());
            List<ItemCompra> itens = itemCompraRepository
                    .findByCompra_IdOrderByOrdemExibicaoAscIdAsc(contexto.compraId());
            assertThat(itens).hasSize(4);
            List<ItemCompra> adicionadosDuranteCompra = itens.stream()
                    .filter(ItemCompra::isAdicionadoDuranteCompra)
                    .toList();
            assertThat(adicionadosDuranteCompra).hasSize(2);
            assertThat(adicionadosDuranteCompra).extracting(ItemCompra::getOrdemExibicao)
                    .containsExactlyInAnyOrder(3, 4);
            assertThat(adicionadosDuranteCompra).allSatisfy(item -> {
                assertThat(item.getItemListaOrigem()).isNull();
                assertThat(item.getAdicionadoEm()).isNotNull();
                assertThat(item.getStatus()).isEqualTo(StatusItemCompra.PENDENTE);
            });
            assertThat(adicionadosDuranteCompra.stream()
                    .filter(item -> item.getDescricaoSnapshot().equals("Cafe"))
                    .findFirst().orElseThrow().getAdicionadoPorParticipanteCompra().getId())
                    .isEqualTo(contexto.primeiroParticipanteCompraId());
            assertThat(adicionadosDuranteCompra.stream()
                    .filter(item -> item.getDescricaoSnapshot().equals("Leite"))
                    .findFirst().orElseThrow().getAdicionadoPorParticipanteCompra().getId())
                    .isEqualTo(contexto.segundoParticipanteCompraId());
            assertThat(itemListaRepository.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ItemCompra adicionar(
            Contexto contexto,
            UUID usuarioId,
            String descricao,
            CountDownLatch prontas,
            CountDownLatch iniciar) throws InterruptedException {
        prontas.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("As chamadas concorrentes nao foram liberadas a tempo.");
        }
        return adicionarItemDuranteCompra.executar(
                usuarioId,
                contexto.familiaId(),
                contexto.listaId(),
                new AdicionarItemDuranteCompraCommand(descricao, BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null));
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
                familia, "Lista", CategoriaCompra.SUPERMERCADO, null, primeiroMembro, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, primeiroMembro, agora));

        Usuario segundoUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Bia", "bia-" + UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia segundoMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarMembro(familia, segundoUsuario, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, segundoMembro, agora));

        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, primeiroMembro, agora));
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Feijao", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 2, primeiroMembro, agora));
        UUID compraId = iniciarCompra.iniciar(primeiroUsuario.getId(), familia.getId(), lista.getId()).compra().getId();
        List<ParticipanteCompra> participantes = participanteCompraRepository
                .findByCompra_IdOrderByGeradoEmAscIdAsc(compraId);
        UUID primeiroParticipanteCompraId = participantes.stream()
                .filter(participante -> participante.getMembroFamilia().getId().equals(primeiroMembro.getId()))
                .findFirst().orElseThrow().getId();
        UUID segundoParticipanteCompraId = participantes.stream()
                .filter(participante -> participante.getMembroFamilia().getId().equals(segundoMembro.getId()))
                .findFirst().orElseThrow().getId();
        return new Contexto(
                familia.getId(), lista.getId(), compraId,
                primeiroUsuario.getId(), segundoUsuario.getId(),
                primeiroParticipanteCompraId, segundoParticipanteCompraId);
    }

    private record Contexto(
            UUID familiaId,
            UUID listaId,
            UUID compraId,
            UUID primeiroUsuarioId,
            UUID segundoUsuarioId,
            UUID primeiroParticipanteCompraId,
            UUID segundoParticipanteCompraId) {
    }
}
