package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
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
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class EditarDadosBasicosListaIntegrationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {
        r.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }
    @Autowired WebApplicationContext context;
    @Autowired CadastrarUsuario cadastrar;
    @Autowired AutenticarUsuario autenticar;
    @Autowired CriarFamilia criarFamilia;
    @Autowired CriarListaCompra criarLista;
    @Autowired AdicionarItemLista adicionarItem;
    @Autowired AdicionarParticipanteLista adicionarParticipante;
    @Autowired EditarDadosBasicosLista editar;
    @Autowired IniciarCompra iniciar;
    @Autowired ListaCompraRepository listas;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    MockMvc mvc;
    Usuario criador;
    UUID familiaId;
    UUID listaId;
    String url;
    static final String BODY = "{\"nome\":\"Lista corrigida\",\"categoria\":\"ROUPAS\",\"estabelecimento\":null}";
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        criador = usuario();
        familiaId = criarFamilia.criar(criador.getId(), "Casa").getId();
        listaId = criarLista.criar(criador.getId(), familiaId, "Original", CategoriaCompra.SUPERMERCADO, "Mercado").getId();
        adicionarItem.adicionar(criador.getId(), familiaId, listaId, "Arroz", BigDecimal.ONE, UnidadeMedida.KG, null, null);
        url = "/api/familias/" + familiaId + "/listas/" + listaId;
    }
    Usuario usuario() { return cadastrar.cadastrar("Pessoa", UUID.randomUUID()+"@test.local", "senha-original"); }
    String token(Usuario u) { return "Bearer " + autenticar.autenticar(u.getEmail(), "senha-original").token(); }
    UUID membro(Usuario u, String papel) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into membro_familia (id,familia_id,usuario_id,papel,status,criado_em,atualizado_em) values (?,?,?,?,'ATIVO',now(),now())", id, familiaId, u.getId(), papel);
        return id;
    }
    void capability(Usuario u, boolean expected) throws Exception {
        mvc.perform(get(url).header("Authorization", token(u))).andExpect(status().isOk())
            .andExpect(jsonPath("$.contextoUsuario.podeEditarDadosBasicos").value(expected));
    }
    @Test void criadorMembroEditaPreservandoIdentidadeItensEParticipantes() throws Exception {
        jdbc.update("update membro_familia set papel='MEMBRO' where usuario_id=?", criador.getId());
        capability(criador, true);
        var original = listas.findById(listaId).orElseThrow();
        mvc.perform(put(url).header("Authorization", token(criador)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Lista corrigida"))
            .andExpect(jsonPath("$.categoria").value("ROUPAS")).andExpect(jsonPath("$.estabelecimento").isEmpty())
            .andExpect(jsonPath("$.id").value(listaId.toString())).andExpect(jsonPath("$.contextoUsuario.podeEditarDadosBasicos").value(true));
        var atual = listas.findById(listaId).orElseThrow();
        assertThat(atual.getCriadaEm()).isEqualTo(original.getCriadaEm());
        assertThat(atual.getAtualizadaEm()).isAfter(original.getAtualizadaEm());
        assertThat(jdbc.queryForObject("select count(*) from item_lista where lista_compra_id=?", Integer.class, listaId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from participante_lista where lista_compra_id=?", Integer.class, listaId)).isEqualTo(1);
        mvc.perform(get(url).header("Authorization", token(criador))).andExpect(jsonPath("$.nome").value("Lista corrigida"));
    }
    @Test void administradorSemParticipacaoPodeEditar() throws Exception {
        Usuario admin = usuario(); membro(admin, "ADMINISTRADOR"); capability(admin, true);
        mvc.perform(put(url).header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false));
    }
    @Test void participanteEObservadorNaoPodemEditar() throws Exception {
        Usuario participante = usuario(); UUID id = membro(participante, "MEMBRO");
        adicionarParticipante.adicionar(criador.getId(), familiaId, listaId, id);
        Usuario observador = usuario(); membro(observador, "MEMBRO");
        for (Usuario u : java.util.List.of(participante, observador)) {
            capability(u, false);
            mvc.perform(put(url).header("Authorization", token(u)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        }
        assertThat(listas.findById(listaId).orElseThrow().getNome()).isEqualTo("Original");
    }
    @Test void rejeitaInativoJwtAusenteEContextoExterno() throws Exception {
        Usuario externo = usuario();
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put(url).header("Authorization", token(externo)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/familias/"+UUID.randomUUID()+"/listas/"+listaId).header("Authorization", token(criador))
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        mvc.perform(put("/api/familias/"+familiaId+"/listas/"+UUID.randomUUID()).header("Authorization", token(criador))
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        jdbc.update("update membro_familia set status='INATIVO' where usuario_id=?", criador.getId());
        mvc.perform(put(url).header("Authorization", token(criador)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }
    @Test void validaMesmoContratoDaCriacao() throws Exception {
        for (String body : java.util.List.of(
            "{\"nome\":\" \",\"categoria\":\"OUTROS\"}", "{\"nome\":\"X\"}",
            "{\"nome\":\"X\",\"categoria\":\"INVALIDA\"}",
            "{\"nome\":\""+ "x".repeat(121) +"\",\"categoria\":\"OUTROS\"}",
            "{\"nome\":\"X\",\"categoria\":\"OUTROS\",\"estabelecimento\":\""+ "x".repeat(121) +"\"}")) {
            mvc.perform(put(url).header("Authorization", token(criador)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(listas.findById(listaId).orElseThrow().getNome()).isEqualTo("Original");
    }
    @ParameterizedTest @ValueSource(strings={"EM_COMPRA","FINALIZADA","CANCELADA"})
    void foraDePreparacaoNaoEdita(String estado) throws Exception {
        jdbc.update("update lista_compra set status=? where id=?", estado, listaId);
        capability(criador, false);
        mvc.perform(put(url).header("Authorization", token(criador)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
        assertThat(listas.findById(listaId).orElseThrow().getNome()).isEqualTo("Original");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void serializaEdicaoEInicio(boolean edicaoPrimeiro) throws Exception {
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
                listas.findByIdForUpdate(listaId).orElseThrow();
                if (edicaoPrimeiro) editar.editar(criador.getId(), familiaId, listaId, "Corrigida", CategoriaCompra.ROUPAS, null);
                else iniciar.iniciar(criador.getId(), familiaId, listaId);
                locked.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                String auth = token(criador);
                var second = pool.submit(() -> edicaoPrimeiro
                    ? mvc.perform(post(url+"/compra").header("Authorization", auth)).andReturn().getResponse().getStatus()
                    : mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse().getStatus());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%lista_compra%')", Boolean.class));
                    if (waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(edicaoPrimeiro ? 201 : 409);
                assertThat(listas.findById(listaId).orElseThrow().getNome()).isEqualTo(edicaoPrimeiro ? "Corrigida" : "Original");
                capability(criador, false);
            } finally { release.countDown(); }
        }
    }
}