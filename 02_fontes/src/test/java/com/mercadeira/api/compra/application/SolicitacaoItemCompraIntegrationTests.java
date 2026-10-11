package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.mercadeira.api.compra.api.CompraResponse;
import com.mercadeira.api.compra.api.SolicitacaoItemCompraResponse;
import com.mercadeira.api.compra.domain.*;
import com.mercadeira.api.compra.repository.*;
import com.mercadeira.api.familia.domain.*;
import com.mercadeira.api.familia.repository.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.lista.repository.*;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
class SolicitacaoItemCompraIntegrationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private SolicitarItemCompraRemoto criar;
    @Autowired private ConsultarSolicitacoesItensCompra consultar;
    @Autowired private DecidirSolicitacaoItemCompra decidir;
    @Autowired private ConsultarCompraDaLista consultarCompra;
    @Autowired private FluxoPresencaCompra presenca;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private FinalizarCompra finalizarCompra;
    @Autowired private ColocarItemNoCarrinho colocarNoCarrinho;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private FamiliaRepository familias;
    @Autowired private MembroFamiliaRepository membros;
    @Autowired private ListaCompraRepository listas;
    @Autowired private ParticipanteListaRepository participantesLista;
    @Autowired private ItemListaRepository itensLista;
    @Autowired private CompraRepository compras;
    @Autowired private ParticipanteCompraRepository participantes;
    @Autowired private ItemCompraRepository itens;
    @Autowired private SolicitacaoItemCompraRepository solicitacoes;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private WebApplicationContext web;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test void remotoCriaPersisteRelacionamentosENomesSemOpenInView() throws Exception {
        var c = contexto();
        var s = pedido(c);
        var response = SolicitacaoItemCompraResponse.from(s);
        assertThat(response.solicitante().id()).isEqualTo(c.remotoParticipante());
        assertThat(response.solicitante().nome()).isEqualTo("Remota");
        assertThat(response.status()).isEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
        assertThat(response.decididoEm()).isNull();
        assertThat(response.decisor()).isNull();
        var salvo = solicitacoes.findById(s.solicitacao().getId()).orElseThrow();
        assertThat(salvo.getCompraId()).isEqualTo(c.compra());
        assertThat(salvo.getSolicitanteId()).isEqualTo(c.remotoParticipante());
        assertThat(salvo.getCriadoEm()).isNotNull();
        mvc.perform(post(url(c)).with(jwt().jwt(j -> j.subject(c.remoto().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Leite\",\"quantidade\":1.5,\"unidadeMedida\":\"LITRO\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.solicitante.nome").value("Remota"))
                .andExpect(jsonPath("$.solicitante.id").value(c.remotoParticipante().toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
        assertThat(consultar.consultar(c.remoto(), c.compra(), null)).hasSize(2); // Sem merge.
    }

    @Test void rejeitaPresenteENaoInformada() throws Exception {
        var c = contexto();
        assertThatThrownBy(() -> criar.solicitar(c.responsavel(), c.compra(), dados())).isInstanceOf(ConflitoPresencaException.class);
        jdbc.update("update participante_compra set presenca_operacional = 'NAO_INFORMADA', presenca_alterada_em = null where id = ?", c.remotoParticipante());
        assertThatThrownBy(() -> pedido(c)).isInstanceOf(ConflitoPresencaException.class);
        mvc.perform(post(url(c)).with(jwt().jwt(j -> j.subject(c.remoto().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Leite\"}"))
                .andExpect(status().isConflict());
    }

    @Test void bloqueiaExternoObservadorVinculoInativoEFamiliaInativa() throws Exception {
        var c = contexto();
        for (UUID usuario : List.of(c.externo(), c.observador())) {
            assertThatThrownBy(() -> criar.solicitar(usuario, c.compra(), dados())).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
            assertThatThrownBy(() -> consultar.consultar(usuario, c.compra(), null)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
            mvc.perform(get(url(c)).with(jwt().jwt(j -> j.subject(usuario.toString())))).andExpect(status().isForbidden());
        }
        jdbc.update("update membro_familia set status = 'INATIVO' where usuario_id = ?", c.remoto());
        assertThatThrownBy(() -> pedido(c)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        jdbc.update("update membro_familia set status = 'ATIVO' where usuario_id = ?", c.remoto());
        jdbc.update("update familia set status = 'INATIVA' where id = ?", c.familia());
        assertThatThrownBy(() -> pedido(c)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThatThrownBy(() -> consultar.consultar(c.remoto(), c.compra(), null)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
    }

    @Test void payloadInvalidoRejeitadoEOpcionaisNulosAceitos() throws Exception {
        var c = contexto();
        for (String payload : List.of("{}", "{\"descricao\":\" \"}", "{\"descricao\":\"Leite\",\"quantidade\":0}",
                "{\"descricao\":\"Leite\",\"quantidade\":-1}", "{\"descricao\":\"Leite\",\"quantidade\":0.0001}",
                "{\"descricao\":\"Leite\",\"quantidade\":1000000000}", "{\"descricao\":\"Leite\",\"unidadeMedida\":\"INVALIDA\"}",
                "{\"descricao\":\"" + "a".repeat(201) + "\"}")) {
            mvc.perform(post(url(c)).with(jwt().jwt(j -> j.subject(c.remoto().toString())))
                    .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(url(c)).with(jwt().jwt(j -> j.subject(c.remoto().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Leite\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantidade").isEmpty());
        mvc.perform(get(url(c))).andExpect(status().isUnauthorized());
    }

    @Test void participanteComVinculoDeOutraFamiliaNaoConcedeAcesso() {
        var c = contexto();
        var outra = contexto();
        tx(() -> {
            var compra = compras.findById(c.compra()).orElseThrow();
            var membro = membros.findByFamilia_IdAndUsuario_IdAndStatus(outra.familia(), outra.remoto(), StatusMembroFamilia.ATIVO).orElseThrow();
            var participante = participantes.save(ParticipanteCompra.criarDireto(compra, membro, Instant.now()));
            participante.alterarPresenca(PresencaOperacional.NAO_PRESENTE, Instant.now());
            return null;
        });
        assertThatThrownBy(() -> criar.solicitar(outra.remoto(), c.compra(), dados())).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThatThrownBy(() -> consultar.consultar(outra.remoto(), c.compra(), null)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
    }

    @Test void listagemIsolaComprasFiltraPendentesEBloqueiaAcessoCruzado() throws Exception {
        var c = contexto();
        var outra = contexto();
        var pendente = pedido(c).solicitacao();
        var recusada = pedido(c).solicitacao();
        decidir.decidir(c.responsavel(), c.compra(), recusada.getId(), false);
        pedido(outra);
        assertThat(consultar.consultar(c.remoto(), c.compra(), null)).hasSize(2);
        assertThat(consultar.consultar(c.remoto(), c.compra(), EstadoSolicitacaoItemCompra.PENDENTE))
                .extracting(r -> r.solicitacao().getId()).containsExactly(pendente.getId());
        assertThatThrownBy(() -> consultar.consultar(c.remoto(), outra.compra(), null)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        mvc.perform(get(url(c)).param("status", "PENDENTE").with(jwt().jwt(j -> j.subject(c.remoto().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].solicitante.nome").value("Remota"));
        mvc.perform(get(url(c)).param("status", "INVALIDO").with(jwt().jwt(j -> j.subject(c.remoto().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test void aprovaCriaExatamenteUmItemPendenteComDadosEAutoriaDaInclusao() throws Exception {
        var c = contexto();
        var s = pedido(c).solicitacao();
        mvc.perform(post(url(c) + "/" + s.getId() + "/aprovar").with(jwt().jwt(j -> j.subject(c.responsavel().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.decisor.nome").value("Responsavel"))
                .andExpect(jsonPath("$.decididoEm").isNotEmpty());
        var salvo = solicitacoes.findById(s.getId()).orElseThrow();
        assertThat(salvo.getDecididoPorId()).isEqualTo(c.responsavelParticipante());
        assertThat(salvo.getDecididoEm()).isNotNull();
        var adicionados = itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra()).stream()
                .filter(ItemCompra::isAdicionadoDuranteCompra).toList();
        assertThat(adicionados).hasSize(1);
        var item = adicionados.getFirst();
        assertThat(item.getStatus()).isEqualTo(StatusItemCompra.PENDENTE);
        assertThat(item.getDescricaoSnapshot()).isEqualTo(s.getDescricao());
        assertThat(item.getQuantidadeSnapshot()).isEqualByComparingTo(s.getQuantidade());
        assertThat(item.getUnidadeMedidaSnapshot()).isEqualTo("LITRO");
        assertThat(item.getAdicionadoPorParticipanteCompra().getId()).isEqualTo(c.responsavelParticipante());
        assertThat(item.getAdicionadoEm()).isEqualTo(salvo.getDecididoEm());
        assertThat(item.getItemListaOrigem()).isNull();
        assertThat(item.getOrdemExibicao()).isEqualTo(2);
        for (boolean aprovar : List.of(true, false)) {
            assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), aprovar))
                    .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
        }
        mvc.perform(post(url(c) + "/" + s.getId() + "/aprovar").with(jwt().jwt(j -> j.subject(c.responsavel().toString()))))
                .andExpect(status().isConflict());
        assertThat(itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra())).hasSize(2);
    }

    @Test void recusaSemCriarItemENaoAceitaSegundaDecisao() throws Exception {
        var c = contexto();
        var s = pedido(c).solicitacao();
        mvc.perform(post(url(c) + "/" + s.getId() + "/recusar").with(jwt().jwt(j -> j.subject(c.responsavel().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.decisor.id").value(c.responsavelParticipante().toString()))
                .andExpect(jsonPath("$.decididoEm").isNotEmpty());
        assertThat(itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra())).hasSize(1);
        for (boolean aprovar : List.of(true, false)) {
            assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), aprovar))
                    .isInstanceOf(SolicitacaoItemCompraNaoPendenteException.class);
        }
    }

    @Test void rollbackReverteSolicitacaoEItemJuntos() {
        var c = contexto();
        var s = pedido(c).solicitacao();
        assertThatThrownBy(() -> tx(() -> {
            decidir.decidir(c.responsavel(), c.compra(), s.getId(), true);
            em.flush();
            throw new IllegalStateException("Falha depois da inclusao.");
        })).isInstanceOf(IllegalStateException.class);
        var salvo = solicitacoes.findById(s.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
        assertThat(salvo.getDecididoPorId()).isNull();
        assertThat(salvo.getDecididoEm()).isNull();
        assertThat(itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra())).hasSize(1);
    }

    @Test void constraintDeQuantidadeContinuaVigente() {
        var c = contexto();
        var s = pedido(c).solicitacao();
        assertThatThrownBy(() -> jdbc.update("update solicitacao_item_compra set quantidade = 0 where id = ?", s.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(solicitacoes.findById(s.getId()).orElseThrow().getQuantidade()).isEqualByComparingTo("1.500");
    }

    @Test void somenteResponsavelAtualDecideNemAdministradorNemExResponsavel() throws Exception {
        var c = contexto();
        var s = pedido(c).solicitacao();
        for (boolean aprovar : List.of(true, false)) {
            assertThatThrownBy(() -> decidir.decidir(c.remoto(), c.compra(), s.getId(), aprovar))
                    .isInstanceOf(AutoridadePresencaException.class); // Remota e ADMINISTRADOR.
        }
        mvc.perform(post(url(c) + "/" + s.getId() + "/aprovar").with(jwt().jwt(j -> j.subject(c.remoto().toString()))))
                .andExpect(status().isForbidden());
        presenca.transferirDiretamente(c.responsavel(), c.familia(), c.lista(), c.outroParticipante());
        assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), true)).isInstanceOf(AutoridadePresencaException.class);
        var resultado = decidir.decidir(c.outro(), c.compra(), s.getId(), true);
        assertThat(resultado.solicitacao().getDecididoPorId()).isEqualTo(c.outroParticipante()); // MEMBRO.
    }

    @Test void responsavelInativoOuNaoPresenteNaoDecide() {
        var c = contexto();
        var s = pedido(c).solicitacao();
        jdbc.update("update participante_compra set presenca_operacional = 'NAO_PRESENTE' where id = ?", c.responsavelParticipante());
        assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), true)).isInstanceOf(AutoridadePresencaException.class);
        jdbc.update("update participante_compra set presenca_operacional = 'PRESENTE' where id = ?", c.responsavelParticipante());
        jdbc.update("update membro_familia set status = 'INATIVO' where usuario_id = ?", c.responsavel());
        assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), false)).isInstanceOf(UsuarioNaoParticipaDaCompraException.class);
        assertThat(solicitacoes.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
    }

    @Test void compraESolicitacaoInexistentesOuDeOutraCompra() throws Exception {
        var c = contexto();
        var outra = contexto();
        var s = pedido(outra).solicitacao();
        assertThatThrownBy(() -> criar.solicitar(c.remoto(), UUID.randomUUID(), dados())).isInstanceOf(CompraNaoEncontradaException.class);
        for (UUID id : List.of(s.getId(), UUID.randomUUID())) {
            assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), id, true))
                    .isInstanceOf(SolicitacaoItemCompraNaoEncontradaException.class);
            mvc.perform(post(url(c) + "/" + id + "/aprovar").with(jwt().jwt(j -> j.subject(c.responsavel().toString()))))
                    .andExpect(status().isNotFound());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void compraFinalizadaOuCanceladaNaoAceitaCriacaoNemDecisao(boolean finalizar) {
        var c = contexto();
        var s = pedido(c).solicitacao();
        if (finalizar) finalizarCompra.executar(c.responsavel(), c.familia(), c.lista());
        else tx(() -> {
            var compra = compras.findById(c.compra()).orElseThrow();
            compra.cancelar(participantes.findById(c.responsavelParticipante()).orElseThrow());
            return null;
        });
        assertThatThrownBy(() -> pedido(c)).isInstanceOf(CompraForaDeAndamentoException.class);
        for (boolean aprovar : List.of(true, false))
            assertThatThrownBy(() -> decidir.decidir(c.responsavel(), c.compra(), s.getId(), aprovar)).isInstanceOf(CompraForaDeAndamentoException.class);
        assertThat(polling(c, c.remoto()).solicitacoesItensPendentes()).isEmpty();
    }

    @Test void pollingExistenteRetornaPendentesENomesECapabilitiesAtualizadas() throws Exception {
        var c = contexto();
        assertThat(polling(c, c.remoto()).solicitacoesItensPendentes()).isEmpty();
        var aprovada = pedido(c).solicitacao();
        var recusada = pedido(c).solicitacao();
        var pendente = pedido(c).solicitacao();
        decidir.decidir(c.responsavel(), c.compra(), aprovada.getId(), true);
        decidir.decidir(c.responsavel(), c.compra(), recusada.getId(), false);
        var response = polling(c, c.remoto());
        assertThat(response.solicitacoesItensPendentes()).extracting(SolicitacaoItemCompraResponse::id).containsExactly(pendente.getId());
        assertThat(response.solicitacoesItensPendentes().getFirst().solicitante().nome()).isEqualTo("Remota");
        assertThat(response.contextoUsuario().podeSolicitarItemRemoto()).isTrue();
        assertThat(response.contextoUsuario().podeDecidirSolicitacoesItens()).isFalse();
        assertThat(polling(c, c.responsavel()).contextoUsuario().podeDecidirSolicitacoesItens()).isTrue();
        assertThat(polling(c, c.observador()).solicitacoesItensPendentes()).isEmpty();
        mvc.perform(get("/api/familias/" + c.familia() + "/listas/" + c.lista() + "/compra")
                .with(jwt().jwt(j -> j.subject(c.responsavel().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitacoesItensPendentes.length()").value(1))
                .andExpect(jsonPath("$.solicitacoesItensPendentes[0].solicitante.nome").value("Remota"))
                .andExpect(jsonPath("$.contextoUsuario.podeDecidirSolicitacoesItens").value(true));
    }

    @Test void remotoNaoPodeColocarItemNoCarrinho() {
        var c = contexto();
        var item = itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra()).getFirst();
        assertThatThrownBy(() -> colocarNoCarrinho.executar(c.remoto(), c.familia(), c.lista(), item.getId()))
                .isInstanceOf(PresencaOperacionalObrigatoriaException.class);
    }

    @Test void consultasNaoCrescemComSolicitantesEDecisoresResolvidosEmLote() {
        var c = contexto();
        pedido(c);
        long poucas = consultas(() -> consultar.consultar(c.remoto(), c.compra(), null).stream().map(SolicitacaoItemCompraResponse::from).toList());
        long pollingPoucas = consultas(() -> polling(c, c.remoto()));
        tx(() -> {
            var compra = compras.findById(c.compra()).orElseThrow();
            var familia = familias.findById(c.familia()).orElseThrow();
            var decisor = participantes.findById(c.responsavelParticipante()).orElseThrow();
            for (int i = 0; i < 20; i++) {
                var u = usuarios.save(Usuario.criar("Remoto " + i, UUID.randomUUID() + "@test.local", "hash", Instant.now()));
                var m = membros.save(MembroFamilia.criarMembro(familia, u, Instant.now()));
                var p = participantes.save(ParticipanteCompra.criarDireto(compra, m, Instant.now()));
                var s = SolicitacaoItemCompra.criar(compra, p, "Item " + i, null, null, Instant.now());
                if (i % 2 == 0) s.recusar(decisor, Instant.now());
                solicitacoes.save(s);
            }
            return null;
        });
        long muitas = consultas(() -> {
            var respostas = consultar.consultar(c.remoto(), c.compra(), null).stream().map(SolicitacaoItemCompraResponse::from).toList();
            assertThat(respostas).hasSize(21);
            assertThat(respostas).allSatisfy(r -> assertThat(r.solicitante().nome()).isNotBlank());
            assertThat(respostas.stream().filter(r -> r.status() == EstadoSolicitacaoItemCompra.RECUSADA))
                    .allSatisfy(r -> assertThat(r.decisor().nome()).isEqualTo("Responsavel"));
            return respostas;
        });
        assertThat(muitas).isEqualTo(poucas);
        assertThat(consultas(() -> polling(c, c.remoto()))).isEqualTo(pollingPoucas);
        assertThat(muitas).isLessThanOrEqualTo(10);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void duasDecisoesSimultaneasSomenteUmaEfetiva(boolean segundaAprova) throws Exception {
        var c = contexto();
        var s = pedido(c).solicitacao();
        var prontas = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> decidirConcorrente(c, s.getId(), true, prontas, liberar));
            var b = executor.submit(() -> decidirConcorrente(c, s.getId(), segundaAprova, prontas, liberar));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            liberar.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            var salvo = solicitacoes.findById(s.getId()).orElseThrow();
            assertThat(salvo.getStatus()).isNotEqualTo(EstadoSolicitacaoItemCompra.PENDENTE);
            assertThat(salvo.getDecididoEm()).isNotNull();
            long criados = itens.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(c.compra()).stream().filter(ItemCompra::isAdicionadoDuranteCompra).count();
            assertThat(criados).isEqualTo(salvo.getStatus() == EstadoSolicitacaoItemCompra.APROVADA ? 1 : 0);
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean decidirConcorrente(Contexto c, UUID id, boolean aprovar, CountDownLatch prontas, CountDownLatch liberar) throws Exception {
        prontas.countDown();
        if (!liberar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout da barreira.");
        try { decidir.decidir(c.responsavel(), c.compra(), id, aprovar); return true; }
        catch (SolicitacaoItemCompraNaoPendenteException esperada) { return false; }
    }

    private long consultas(Supplier<?> consulta) {
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        consulta.get();
        return statistics.getPrepareStatementCount();
    }
    private CompraResponse polling(Contexto c, UUID usuario) {
        return CompraResponse.from(consultarCompra.consultar(usuario, c.familia(), c.lista()), usuario);
    }
    private ResultadoSolicitacaoItemCompra pedido(Contexto c) { return criar.solicitar(c.remoto(), c.compra(), dados()); }
    private SolicitarItemCompraRemotoCommand dados() { return new SolicitarItemCompraRemotoCommand("Leite", new BigDecimal("1.500"), UnidadeMedida.LITRO); }
    private String url(Contexto c) { return "/api/compras/" + c.compra() + "/solicitacoes-itens"; }
    private <T> T tx(Supplier<T> trabalho) { return new TransactionTemplate(transactionManager).execute(s -> trabalho.get()); }

    private Contexto contexto() {
        return tx(() -> {
            var agora = Instant.now();
            var responsavel = usuario("Responsavel");
            var familia = familias.save(Familia.criar("Casa", UUID.randomUUID().toString().replace("-", ""), responsavel, agora));
            var iniciador = membros.save(MembroFamilia.criarAdministrador(familia, responsavel, agora));
            var remota = usuario("Remota");
            var remoto = membros.save(MembroFamilia.criarAdministrador(familia, remota, agora));
            var outro = usuario("Outro");
            var membroOutro = membros.save(MembroFamilia.criarMembro(familia, outro, agora));
            var observador = usuario("Observador");
            membros.save(MembroFamilia.criarMembro(familia, observador, agora));
            var lista = listas.save(ListaCompra.criar(familia, "Lista", CategoriaCompra.SUPERMERCADO, null, iniciador, agora));
            for (var m : List.of(iniciador, remoto, membroOutro)) participantesLista.save(ParticipanteLista.criar(lista, m, agora));
            itensLista.save(ItemLista.criar(lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, iniciador, agora));
            em.flush();
            var compra = iniciarCompra.iniciar(responsavel.getId(), familia.getId(), lista.getId(), Set.of(membroOutro.getId())).compra();
            var todos = participantes.findByCompra_IdOrderByGeradoEmAscIdAsc(compra.getId());
            var pRemoto = todos.stream().filter(p -> p.getMembroFamilia().getId().equals(remoto.getId())).findFirst().orElseThrow();
            pRemoto.alterarPresenca(PresencaOperacional.NAO_PRESENTE, Instant.now());
            var pOutro = todos.stream().filter(p -> p.getMembroFamilia().getId().equals(membroOutro.getId())).findFirst().orElseThrow();
            return new Contexto(familia.getId(), lista.getId(), compra.getId(), responsavel.getId(), remota.getId(),
                    outro.getId(), observador.getId(), usuario("Externo").getId(), compra.getResponsavelOperacionalId(), pRemoto.getId(), pOutro.getId());
        });
    }
    private Usuario usuario(String nome) { return usuarios.save(Usuario.criar(nome, UUID.randomUUID() + "@test.local", "hash", Instant.now())); }
    private record Contexto(UUID familia, UUID lista, UUID compra, UUID responsavel, UUID remoto, UUID outro,
            UUID observador, UUID externo, UUID responsavelParticipante, UUID remotoParticipante, UUID outroParticipante) {}
}
