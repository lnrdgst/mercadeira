package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.function.Supplier;
import java.time.Clock;
import com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class DecidirRemocaoItemCompraConcorrenciaIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private AprovarRemocaoItemCompra aprovar;
    @Autowired private RejeitarRemocaoItemCompra rejeitar;
    @Autowired private SolicitarRemocaoItemCompra solicitar;
    @Autowired private ColocarItemNoCarrinho colocar;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroRepository;
    @Autowired private ListaCompraRepository listaRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private Clock clock;

    @Test
    void aprovarERejeitarConcorrentementePermitemUmaUnicaDecisao() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-09-09T12:00:00Z"));
        Contexto contexto = criarContextoPendente();
        var solicitacaoOriginal = jdbcTemplate.queryForMap("select * from item_compra where id = ?", contexto.itemId());
        Instant decisaoEm = Instant.parse("2026-09-09T12:01:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(decisaoEm);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResultadoDecisaoRemocaoItemCompra> aprovacao = executor.submit(() -> decidir(prontas, iniciar, () -> aprovar.executar(
                    contexto.usuarioResponsavelId(), contexto.familiaId(), contexto.listaId(), contexto.itemId())));
            Future<ResultadoDecisaoRemocaoItemCompra> rejeicao = executor.submit(() -> decidir(prontas, iniciar, () -> rejeitar.executar(
                    contexto.usuarioResponsavelId(), contexto.familiaId(), contexto.listaId(), contexto.itemId())));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();

            var resultadoAprovacao = concluir(aprovacao);
            var resultadoRejeicao = concluir(rejeicao);
            assertThat((resultadoAprovacao != null ? 1 : 0) + (resultadoRejeicao != null ? 1 : 0)).isEqualTo(1);
            var vencedor = resultadoAprovacao != null ? resultadoAprovacao : resultadoRejeicao;
            assertThat(vencedor.decisaoAplicada()).isTrue();
            var persistido = jdbcTemplate.queryForMap("select * from item_compra where id = ?", contexto.itemId());
            assertThat(persistido.get("status")).isEqualTo(resultadoAprovacao != null ? "REMOVIDO" : "NO_CARRINHO");
            assertThat(persistido.get("decisao_remocao")).isEqualTo(resultadoAprovacao != null ? "APROVADA" : "REJEITADA");
            assertThat(persistido.get("remocao_resolvida_por_membro_familia_id")).isEqualTo(solicitacaoOriginal.get("marcado_por_membro_familia_id"));
            assertThat(persistido.get("remocao_resolvida_em")).isEqualTo(java.sql.Timestamp.from(decisaoEm));
            assertThat(persistido.get("remocao_solicitada_por_membro_familia_id")).isEqualTo(solicitacaoOriginal.get("remocao_solicitada_por_membro_familia_id"));
            assertThat(persistido.get("remocao_solicitada_em")).isEqualTo(solicitacaoOriginal.get("remocao_solicitada_em"));
            assertThat(persistido.get("marcado_por_membro_familia_id")).isEqualTo(solicitacaoOriginal.get("marcado_por_membro_familia_id"));
            assertThat(persistido.get("marcado_em")).isEqualTo(solicitacaoOriginal.get("marcado_em"));
            assertThat(jdbcTemplate.queryForObject("select count(*) from item_compra where id = ? and decisao_remocao is not null", Integer.class, contexto.itemId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForMap("select * from item_compra where id = ?", contexto.itemId())).isEqualTo(persistido);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResultadoDecisaoRemocaoItemCompra concluir(Future<ResultadoDecisaoRemocaoItemCompra> future) throws InterruptedException {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isExactlyInstanceOf(TransicaoStatusItemCompraInvalidaException.class);
            return null;
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new AssertionError(exception);
        }
    }

    private ResultadoDecisaoRemocaoItemCompra decidir(CountDownLatch prontas, CountDownLatch iniciar, Supplier<ResultadoDecisaoRemocaoItemCompra> acao) {
        prontas.countDown();
        try {
            if (!iniciar.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("As decisoes concorrentes nao foram liberadas a tempo.");
            }
            return acao.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Contexto criarContextoPendente() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario responsavel = usuarioRepository.saveAndFlush(Usuario.criar("Ana", "ana" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar("Familia", UUID.randomUUID().toString().replace("-", ""), responsavel, agora));
        MembroFamilia membroResponsavel = membroRepository.saveAndFlush(MembroFamilia.criarAdministrador(familia, responsavel, agora));
        ListaCompra lista = listaRepository.saveAndFlush(ListaCompra.criar(familia, "Lista", CategoriaCompra.OUTROS, null, membroResponsavel, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membroResponsavel, agora));
        Usuario solicitante = usuarioRepository.saveAndFlush(Usuario.criar("Bia", "bia" + UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia membroSolicitante = membroRepository.saveAndFlush(MembroFamilia.criarMembro(familia, solicitante, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, membroSolicitante, agora));
        itemListaRepository.saveAndFlush(ItemLista.criar(lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membroResponsavel, agora));
        UUID compraId = iniciarCompra.iniciar(responsavel.getId(), familia.getId(), lista.getId(),
                Set.of(membroResponsavel.getId(), membroSolicitante.getId())).compra().getId();
        UUID itemId = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId).getFirst().getId();
        colocar.executar(responsavel.getId(), familia.getId(), lista.getId(), itemId);
        solicitar.executar(solicitante.getId(), familia.getId(), lista.getId(), itemId);
        return new Contexto(responsavel.getId(), familia.getId(), lista.getId(), itemId);
    }

    private record Contexto(UUID usuarioResponsavelId, UUID familiaId, UUID listaId, UUID itemId) {
    }
}
