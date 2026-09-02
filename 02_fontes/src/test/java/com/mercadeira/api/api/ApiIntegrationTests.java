package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@Transactional
@Rollback
class ApiIntegrationTests {

    private static final String JWT_TEST_SECRET = Base64.getEncoder().encodeToString(
            "segredo-exclusivo-de-teste-com-32-bytes-ou-mais".getBytes(StandardCharsets.UTF_8));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> JWT_TEST_SECRET);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private CadastrarUsuario cadastrarUsuario;
    @Autowired private AutenticarUsuario autenticarUsuario;
    @Autowired private CriarFamilia criarFamilia;
    @Autowired private SolicitarEntradaFamiliaPorCodigo solicitarEntrada;
    @Autowired private SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    @Autowired private CriarListaCompra criarListaCompra;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listaFamiliasAtivasOuArrayVazio() throws Exception {
        Usuario semFamilia = usuario("Sem familia");
        mockMvc.perform(get("/api/familias").header("Authorization", bearer(semFamilia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        Usuario usuario = usuario("Ana");
        criarFamilia.criar(usuario.getId(), "Zeta");
        criarFamilia.criar(usuario.getId(), "Alfa");
        mockMvc.perform(get("/api/familias").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Alfa"))
                .andExpect(jsonPath("$[0].papel").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[1].nome").value("Zeta"));
    }

    @Test
    void rotasDeFamiliaExigemJwt() throws Exception {
        mockMvc.perform(get("/api/familias")).andExpect(status().isUnauthorized());
    }

    @Test
    void solicitacaoEOnboardingFuncionamComOutraFamiliaAtiva() throws Exception {
        Usuario usuario = usuario("Bia");
        criarFamilia.criar(usuario.getId(), "Casa Bia");
        Usuario admin = usuario("Admin");
        Familia destino = criarFamilia.criar(admin.getId(), "Destino");

        mockMvc.perform(post("/api/familias/solicitacoes").header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoIngresso\":\"" + destino.getCodigoIngresso() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"));
        mockMvc.perform(get("/api/familias/solicitacoes/minhas-pendentes").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].familia.id").value(destino.getId().toString()));
    }

    @Test
    void aprovacaoEscopadaNaoCancelaOutrasPendencias() throws Exception {
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");
        Usuario adminB = usuario("Admin B");
        Familia familiaB = criarFamilia.criar(adminB.getId(), "Familia B");
        Usuario solicitante = usuario("Bia");
        UUID solicitacaoA = solicitarEntrada.solicitar(solicitante.getId(), familiaA.getCodigoIngresso()).getId();
        UUID solicitacaoB = solicitarEntrada.solicitar(solicitante.getId(), familiaB.getCodigoIngresso()).getId();

        mockMvc.perform(post("/api/familias/{familiaId}/solicitacoes/{id}/aprovar", familiaA.getId(), solicitacaoA)
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        assertThat(solicitacaoRepository.findById(solicitacaoB).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacaoEntradaFamilia.PENDENTE);
        mockMvc.perform(get("/api/familias/{familiaId}/solicitacoes", familiaB.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointAdministrativoExigeFamiliaDaSolicitacao() throws Exception {
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");
        Usuario adminB = usuario("Admin B");
        Familia familiaB = criarFamilia.criar(adminB.getId(), "Familia B");
        Usuario solicitante = usuario("Bia");
        SolicitacaoEntradaFamilia solicitacao = solicitarEntrada.solicitar(solicitante.getId(), familiaB.getCodigoIngresso());

        mockMvc.perform(post("/api/familias/{familiaId}/solicitacoes/{id}/aprovar", familiaA.getId(), solicitacao.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void preparaListaPorApiNoContextoDaFamilia() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familiaA = criarFamilia.criar(ana.getId(), "Familia A");
        Usuario bia = usuario("Bia");
        Familia familiaB = criarFamilia.criar(bia.getId(), "Familia B");

        mockMvc.perform(post("/api/familias/{familiaId}/listas", familiaA.getId()).header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Compra semanal\",\"categoria\":\"SUPERMERCADO\",\"estabelecimento\":\"Mercado\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EM_PREPARACAO"));
        ListaCompra lista = criarListaCompra.criar(ana.getId(), familiaA.getId(), "Outra", CategoriaCompra.OUTROS, null);
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familiaA.getId(), lista.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(lista.getId().toString()));
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familiaB.getId(), lista.getId()).header("Authorization", bearer(bia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioAutenticadoConsultaPropriosDadosSemCredenciais() throws Exception {
        Usuario usuario = usuario("Leonardo");
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Leonardo")).andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.senhaHash").doesNotExist());
        mockMvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void membroAtivoListaMembrosAtivosOrdenadosESemAcessoExterno() throws Exception {
        Usuario ana = usuario("Ana"); Familia familiaA = criarFamilia.criar(ana.getId(), "A");
        Usuario bia = usuario("Bia");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", UUID.randomUUID(), familiaA.getId(), bia.getId());
        Usuario inativo = usuario("Zoe");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'INATIVO', now(), now())", UUID.randomUUID(), familiaA.getId(), inativo.getId());
        mockMvc.perform(get("/api/familias/{id}/membros", familiaA.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Ana")).andExpect(jsonPath("$[0].membroFamiliaId").exists())
                .andExpect(jsonPath("$[0].usuarioId").exists()).andExpect(jsonPath("$[0].email").exists()).andExpect(jsonPath("$[0].papel").exists());
        Familia familiaB = criarFamilia.criar(inativo.getId(), "B");
        mockMvc.perform(get("/api/familias/{id}/membros", familiaA.getId()).header("Authorization", bearer(inativo)))
                .andExpect(status().isForbidden());
        assertThat(familiaB).isNotNull();
    }

    @Test
    void detalheDaListaExplicitaCriadorEContextoDoParticipante() throws Exception {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(ana.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familia.getId(), lista.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.criador.membroFamiliaId").value(lista.getCriadaPorMembroFamilia().getId().toString()))
                .andExpect(jsonPath("$.criador.usuarioId").value(ana.getId().toString())).andExpect(jsonPath("$.criador.nome").value("Ana"))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(true));
    }

    @Test
    void detalheDistingueAdministradorEMembroNaoParticipantes() throws Exception {
        Usuario criador = usuario("Criador"); Familia familia = criarFamilia.criar(criador.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(criador.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        Usuario admin = usuario("Admin"); Usuario membro = usuario("Membro"); entityManager.flush();
        UUID adminId = UUID.randomUUID(); UUID membroId = UUID.randomUUID();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'ATIVO', now(), now())", adminId, familia.getId(), admin.getId());
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", membroId, familia.getId(), membro.getId());
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(membro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(false)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
        mockMvc.perform(post("/api/familias/{f}/listas/{l}/participantes", familia.getId(), lista.getId()).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"membroFamiliaId\":\"" + adminId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(true));
    }

    @Test
    void detalheNaoPermiteAlterarItensForaDePreparacao() throws Exception {
        Usuario usuario = usuario("Ana"); Familia familia = criarFamilia.criar(usuario.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(usuario.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        entityManager.flush();
        jdbcTemplate.update("update lista_compra set status = 'EM_COMPRA' where id = ?", lista.getId());
        entityManager.clear();
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
    }

    private Usuario usuario(String nome) {
        return cadastrarUsuario.cadastrar(nome, nome.toLowerCase().replace(" ", "") + UUID.randomUUID() + "@test.local", "senha-original");
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + autenticarUsuario.autenticar(usuario.getEmail(), "senha-original").token();
    }
}
