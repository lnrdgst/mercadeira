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
class CompraLockIntegrationTests {

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


    @Autowired private SolicitarRemocaoItemCompra solicitar;
    @Autowired private AprovarRemocaoItemCompra aprovar;
    @Autowired private RejeitarRemocaoItemCompra rejeitar;
    @Autowired private AdicionarItemDuranteCompra adicionar;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"carrinho", "solicitar", "aprovar", "rejeitar", "adicionar"})
    void aguardaCompraSemBloquearOutraCompraELiberaItemSomenteDepois(String operacao) throws Exception {
        verificarFronteira(operacao, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"carrinho", "solicitar", "aprovar", "rejeitar", "adicionar"})
    void revalidaStatusDepoisDeAdquirirCompraSemGravarItem(String operacao) throws Exception {
        verificarFronteira(operacao, true);
    }

    private void verificarFronteira(String operacao, boolean cancelarSobLock) throws Exception {
        Contexto contexto = preparar(operacao);
        Contexto independente = preparar(operacao);
        var itensAntes = itens(contexto);
        var lockAdquirido = new java.util.concurrent.CompletableFuture<Integer>();
        var mutacaoIniciada = new java.util.concurrent.CompletableFuture<Integer>();
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        var transacao = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try {
            Future<?> bloqueadora = executor.submit(() -> transacao.executeWithoutResult(tx -> {
                compraRepository.findByListaCompra_IdForUpdate(contexto.listaId()).orElseThrow();
                lockAdquirido.complete(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                try {
                    if (!liberar.await(60, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Teste nao liberou o lock da compra.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                if (cancelarSobLock) {
                    // Fixture estrutural; nao introduz caso de uso de encerramento.
                    jdbcTemplate.update("update compra set status = 'CANCELADA' where id = ?", contexto.compraId());
                }
            }));
            int pidA = lockAdquirido.get(10, TimeUnit.SECONDS);
            Future<?> mutacao = executor.submit(() -> transacao.executeWithoutResult(tx -> {
                jdbcTemplate.execute("set local lock_timeout = '30s'");
                mutacaoIniciada.complete(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                executar(operacao, contexto);
            }));
            int pidB = mutacaoIniciada.get(10, TimeUnit.SECONDS);
            aguardarBloqueioPostgres(pidA, pidB, mutacao);
            assertThat(mutacao.isDone()).isFalse();
            assertThat(itens(contexto)).isEqualTo(itensAntes);

            // B esta esperando Compra: ainda nao pode ter adquirido ItemCompra.
            transacao.executeWithoutResult(tx -> assertThat(jdbcTemplate.queryForObject(
                    "select id from item_compra where id = ? for update nowait", UUID.class,
                    contexto.itemCompraId())).isEqualTo(contexto.itemCompraId()));

            // A continua com o lock enquanto outra compra conclui a MESMA operacao.
            Future<?> outra = executor.submit(() -> executar(operacao, independente));
            outra.get(10, TimeUnit.SECONDS);
            assertThat(itens(independente)).isNotEmpty();
            validarResultado(operacao, independente);
            assertThat(mutacao.isDone()).isFalse();

            liberar.countDown();
            bloqueadora.get(10, TimeUnit.SECONDS);
            if (cancelarSobLock) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutacao.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(CompraForaDeAndamentoException.class);
                assertThat(itens(contexto)).isEqualTo(itensAntes);
            } else {
                mutacao.get(10, TimeUnit.SECONDS);
                validarResultado(operacao, contexto);
            }
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void aguardarBloqueioPostgres(int pidA, int pidB, Future<?> mutacao) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            Boolean bloqueado = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from pg_stat_activity
                        where pid = ? and wait_event_type = 'Lock'
                        and ? = any(pg_blocking_pids(pid))
                        and query like '%from compra %'
                    )
                    """, Boolean.class, pidB, pidA);
            if (Boolean.TRUE.equals(bloqueado)) return;
            if (mutacao.isDone()) {
                mutacao.get(1, TimeUnit.SECONDS);
                throw new AssertionError("Mutacao atravessou Compra enquanto outra transacao mantinha o lock.");
            }
            // Poll limitado apenas no teste; sucesso depende do lock observado no servidor.
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < limite);
        throw new AssertionError("PostgreSQL nao confirmou espera pelo lock da Compra.");
    }

    private Contexto preparar(String operacao) {
        Contexto c = criarContexto();
        if (List.of("solicitar", "aprovar", "rejeitar").contains(operacao)) {
            colocarItemNoCarrinho.executar(c.primeiroUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
        }
        if (List.of("aprovar", "rejeitar").contains(operacao)) {
            solicitar.executar(c.segundoUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
        }
        return c;
    }

    private void executar(String operacao, Contexto c) {
        switch (operacao) {
            case "carrinho" -> colocarItemNoCarrinho.executar(c.primeiroUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
            case "solicitar" -> solicitar.executar(c.segundoUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
            case "aprovar" -> aprovar.executar(c.primeiroUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
            case "rejeitar" -> rejeitar.executar(c.primeiroUsuarioId(), c.familiaId(), c.listaId(), c.itemCompraId());
            case "adicionar" -> adicionar.executar(c.primeiroUsuarioId(), c.familiaId(), c.listaId(),
                    new AdicionarItemDuranteCompraCommand("Leite", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null));
            default -> throw new IllegalArgumentException(operacao);
        }
    }

    private void validarResultado(String operacao, Contexto c) {
        if (operacao.equals("adicionar")) {
            assertThat(itens(c)).hasSize(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select max(ordem_exibicao) from item_compra where compra_id = ?", Integer.class, c.compraId())).isEqualTo(2);
            return;
        }
        String esperado = switch (operacao) {
            case "solicitar" -> "REMOCAO_SOLICITADA";
            case "aprovar" -> "REMOVIDO";
            default -> "NO_CARRINHO";
        };
        assertThat(jdbcTemplate.queryForObject("select status from item_compra where id = ?", String.class,
                c.itemCompraId())).isEqualTo(esperado);
    }

    private List<java.util.Map<String, Object>> itens(Contexto c) {
        return jdbcTemplate.queryForList("select * from item_compra where compra_id = ? order by ordem_exibicao, id", c.compraId());
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
